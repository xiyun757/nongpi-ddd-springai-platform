#!/usr/bin/env node
/**
 * 天气 MCP Server - Open-Meteo（免费、无需 API Key）
 * 暴露 2 个工具：
 *   - getWeather(city)：查未来 3 天天气
 *   - getWeatherAlert(city)：查天气预警建议（高温/暴雨）
 *
 * 30 行核心逻辑，无依赖（用 Node.js 内置 http 模块）
 */
const http = require('https');
const { Server } = require('@modelcontextprotocol/sdk/server/index.js');
const { StdioServerTransport } = require('@modelcontextprotocol/sdk/server/stdio.js');
const { CallToolRequestSchema, ListToolsRequestSchema } = require('@modelcontextprotocol/sdk/types.js');

// --- HTTP GET 工具函数 ---
function httpGet(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (res) => {
      let data = '';
      res.on('data', (chunk) => (data += chunk));
      res.on('end', () => {
        try { resolve(JSON.parse(data)); } catch (e) { reject(e); }
      });
    }).on('error', reject);
  });
}

// 中文城市/省份 → 拼音映射表
// Open-Meteo geocoding 对中文支持不完整（北京/上海 OK，地级市如泉州返回空）
// 命中映射表则用拼音查询；未命中则原样用中文查（大城市仍可识别）
// 如需扩展直接往表里追加
const CITY_PINYIN_MAP = {
  // 直辖市
  '北京': 'Beijing', '上海': 'Shanghai', '天津': 'Tianjin', '重庆': 'Chongqing',
  // 省会城市
  '广州': 'Guangzhou', '深圳': 'Shenzhen', '杭州': 'Hangzhou', '南京': 'Nanjing',
  '成都': 'Chengdu', '武汉': 'Wuhan', '西安': "Xi'an", '长沙': 'Changsha',
  '苏州': 'Suzhou', '郑州': 'Zhengzhou', '青岛': 'Qingdao', '沈阳': 'Shenyang',
  '大连': 'Dalian', '哈尔滨': 'Harbin', '济南': 'Jinan', '合肥': 'Hefei',
  '福州': 'Fuzhou', '南昌': 'Nanchang', '石家庄': 'Shijiazhuang', '太原': 'Taiyuan',
  '长春': 'Changchun', '贵阳': 'Guiyang', '昆明': 'Kunming', '南宁': 'Nanning',
  '海口': 'Haikou', '兰州': 'Lanzhou', '西宁': 'Xining', '银川': 'Yinchuan',
  '乌鲁木齐': 'Urumqi', '呼和浩特': 'Hohhot', '拉萨': 'Lhasa',
  // 福建全省（项目背景：农批履约涉及福建供应链）
  '泉州': 'Quanzhou', '厦门': 'Xiamen', '莆田': 'Putian', '漳州': 'Zhangzhou',
  '龙岩': 'Longyan', '三明': 'Sanming', '南平': 'Nanping', '宁德': 'Ningde',
  // 省份（用户问"福建今天天气"时，取省会代表）
  '福建': 'Fuzhou', '广东': 'Guangzhou', '浙江': 'Hangzhou', '江苏': 'Nanjing',
  '山东': 'Jinan', '四川': 'Chengdu', '湖北': 'Wuhan', '湖南': 'Changsha',
  '河南': 'Zhengzhou', '河北': 'Shijiazhuang', '辽宁': 'Shenyang',
  '安徽': 'Hefei', '江西': 'Nanchang', '山西': 'Taiyuan', '吉林': 'Changchun',
  '黑龙江': 'Harbin', '贵州': 'Guiyang', '云南': 'Kunming', '广西': 'Nanning',
  '海南': 'Haikou', '甘肃': 'Lanzhou', '青海': 'Xining', '宁夏': 'Yinchuan',
  '新疆': 'Urumqi', '内蒙古': 'Hohhot', '西藏': 'Lhasa',
};

// --- 天气查询核心逻辑 ---
async function getWeather(city, days = 3) {
  // 0. 参数校验：days 范围 1-7
  days = Math.max(1, Math.min(7, parseInt(days) || 3));

  // 0.5 中文→拼音转译（Open-Meteo geocoding 对中文地级市支持差）
  const queryName = CITY_PINYIN_MAP[city] || city;

  // 1. 城市名 → 经纬度（Open-Meteo geocoding）
  const geoUrl = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(queryName)}&count=1&language=zh`;
  const geo = await httpGet(geoUrl);
  if (!geo.results || geo.results.length === 0) {
    return { error: `未找到城市：${city}` };
  }
  const { latitude, longitude, name, country } = geo.results[0];

  // 2. 经纬度 → N 天天气预报
  const weatherUrl = `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=Asia/Shanghai&forecast_days=${days}`;
  const weather = await httpGet(weatherUrl);
  const dates = weather.daily.time;
  const maxTemps = weather.daily.temperature_2m_max;
  const minTemps = weather.daily.temperature_2m_min;
  const precip = weather.daily.precipitation_sum;

  // 3. 格式化为易读文本
  const lines = dates.map((d, i) =>
    `${d}: 最高${maxTemps[i]}°C 最低${minTemps[i]}°C 降水${precip[i]}mm`
  ).join('\n');

  return {
    city: `${name}, ${country}`,
    forecast: lines,
    raw: { maxTemps, minTemps, precip }
  };
}

// --- 天气预警判断（农批场景）---
function judgeAlert(weather) {
  if (weather.error) return weather;
  const { maxTemps, precip } = weather.raw;
  const alerts = [];

  // 高温预警：连续 3 天最高 ≥ 35°C（常温区干货变质风险）
  const highTempDays = maxTemps.filter(t => t >= 35).length;
  if (highTempDays >= 2) {
    alerts.push(`高温预警：未来 3 天有 ${highTempDays} 天最高温 ≥ 35°C，建议常温区缩短临期预警阈值（7 天 → 5 天）`);
  }

  // 暴雨预警：任一天降水 ≥ 10mm（运输延误风险）
  const heavyRainDays = precip.filter(p => p >= 10).length;
  if (heavyRainDays >= 1) {
    alerts.push(`暴雨预警：未来 3 天有 ${heavyRainDays} 天降水 ≥ 10mm，建议延迟从该地区供应商的入库计划`);
  }

  // 低温预警：任一天最低 ≤ 0°C（冷藏区商品冻损风险）
  const frostDays = weather.raw.minTemps.filter(t => t <= 0).length;
  if (frostDays >= 1) {
    alerts.push(`低温预警：未来 3 天有 ${frostDays} 天最低温 ≤ 0°C，建议检查冷藏区温度设置`);
  }

  return alerts.length > 0
    ? { city: weather.city, forecast: weather.forecast, alerts }
    : { city: weather.city, forecast: weather.forecast, alerts: ['无天气预警，正常运营'] };
}

// --- MCP Server 定义 ---
const server = new Server(
  { name: 'weather-mcp-server', version: '1.0.0' },
  { capabilities: { tools: {} } }
);

// 列出工具
server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: 'getWeather',
      description: '查询指定城市或省份未来天气预报（最高/最低气温、降水量）。用户询问任何地区的天气情况时都必须调用此工具，包括省份名（如"福建"会自动转译为省会福州查询）。用户问"今天"传 days=1，问"未来几天"传 days=3，默认 3 天。',
      inputSchema: {
        type: 'object',
        properties: {
          city: { type: 'string', description: '城市或省份名（支持中文，如 北京、泉州、福建、上海）' },
          days: { type: 'integer', description: '查询天数（1-7），用户问"今天"传 1，问"未来 3 天"传 3，默认 3', default: 3 }
        },
        required: ['city']
      }
    },
    {
      name: 'getWeatherAlert',
      description: '查询指定城市的天气预警及农批履约建议（高温/暴雨/低温预警）。用于用户询问天气对库存影响、是否需要调整预警阈值时调用。默认查 3 天。',
      inputSchema: {
        type: 'object',
        properties: {
          city: { type: 'string', description: '城市名（支持中文）' },
          days: { type: 'integer', description: '查询天数（1-7），默认 3', default: 3 }
        },
        required: ['city']
      }
    }
  ]
}));

// 执行工具
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;
  try {
    if (name === 'getWeather') {
      const result = await getWeather(args.city, args.days);
      return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) }] };
    }
    if (name === 'getWeatherAlert') {
      const weather = await getWeather(args.city, args.days);
      const result = judgeAlert(weather);
      return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) }] };
    }
    return { content: [{ type: 'text', text: `未知工具：${name}` }] };
  } catch (e) {
    return { content: [{ type: 'text', text: `工具执行异常：${e.message}` }] };
  }
});

// 启动（stdio 模式）
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error('[weather-mcp-server] 已启动，等待 MCP Client 连接...');
}

main().catch(console.error);

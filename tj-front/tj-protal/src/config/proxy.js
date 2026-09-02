// Local development talks to the host-published gateway. Override this with
// VITE_API_BASE_URL when the gateway is exposed at another address.
const localHost = import.meta.env.VITE_API_BASE_URL || 'http://localhost:10010';

export default {
  development: {
    // 开发环境接口请求
    host: localHost,
    // 开发环境 cdn 路径
    cdn: '',
  },
  test: {
    // 测试环境接口地址
    host: localHost,
    // 测试环境 cdn 路径
    cdn: '',
  },
  product: {
    // 正式环境接口地址
    host: localHost,
    // 正式环境 cdn 路径
    cdn: '',
  },
};

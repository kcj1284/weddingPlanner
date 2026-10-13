import { Container } from '@cloudflare/containers';

interface DeploymentEnv {
  CHEONGMO: DurableObjectNamespace<CheongmoContainer>;
  PUBLIC_BASE_URL: string;
  DATABASE_URL: string;
  DATABASE_USERNAME: string;
  DATABASE_PASSWORD: string;
  INVITE_CODE_ENCRYPTION_KEY: string;
  SMTP_HOST?: string;
  SMTP_PORT?: string;
  SMTP_USERNAME?: string;
  SMTP_PASSWORD?: string;
  MAIL_FROM?: string;
  KAKAO_REST_API_KEY?: string;
  KAKAO_JAVASCRIPT_KEY?: string;
  NAVER_SEARCH_CLIENT_ID?: string;
  NAVER_SEARCH_CLIENT_SECRET?: string;
  KAKAO_CLIENT_ID?: string;
  KAKAO_CLIENT_SECRET?: string;
  GOOGLE_CLIENT_ID?: string;
  GOOGLE_CLIENT_SECRET?: string;
  NAVER_CLIENT_ID?: string;
  NAVER_CLIENT_SECRET?: string;
}

const requiredSettings = [
  'DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD', 'INVITE_CODE_ENCRYPTION_KEY'
] as const;

export class CheongmoContainer extends Container<DeploymentEnv> {
  defaultPort = 8080;
  enableInternet = true;
  sleepAfter = '15m';

  constructor(ctx: DurableObjectState<{}>, env: DeploymentEnv) {
    super(ctx, env);
    // Only explicit application settings cross into the Java process.
    this.envVars = {
      SPRING_PROFILES_ACTIVE: 'postgres',
      SERVER_ADDRESS: '0.0.0.0',
      PORT: '8080',
      PUBLIC_BASE_URL: env.PUBLIC_BASE_URL,
      DATABASE_URL: env.DATABASE_URL,
      DATABASE_USERNAME: env.DATABASE_USERNAME,
      DATABASE_PASSWORD: env.DATABASE_PASSWORD,
      INVITE_CODE_ENCRYPTION_KEY: env.INVITE_CODE_ENCRYPTION_KEY
    };
    const optionalSettings = [
      'SMTP_HOST', 'SMTP_PORT', 'SMTP_USERNAME', 'SMTP_PASSWORD', 'MAIL_FROM',
      'KAKAO_REST_API_KEY', 'KAKAO_JAVASCRIPT_KEY',
      'NAVER_SEARCH_CLIENT_ID', 'NAVER_SEARCH_CLIENT_SECRET',
      'KAKAO_CLIENT_ID', 'KAKAO_CLIENT_SECRET', 'GOOGLE_CLIENT_ID',
      'GOOGLE_CLIENT_SECRET', 'NAVER_CLIENT_ID', 'NAVER_CLIENT_SECRET'
    ] as const;
    for (const name of optionalSettings) {
      const value = env[name];
      if (value) this.envVars[name] = value;
    }
  }
}

export default {
  async fetch(request: Request, env: DeploymentEnv): Promise<Response> {
    if (requiredSettings.some(name => !env[name])) {
      return new Response('서비스 배포 설정을 준비하고 있습니다.', {
        status: 503,
        headers: { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' }
      });
    }
    const url = new URL(request.url);
    if (url.protocol !== 'https:') {
      url.protocol = 'https:';
      return Response.redirect(url.href, 308);
    }
    const forwarded = new Request(request);
    for (const name of ['forwarded', 'x-forwarded-for', 'x-forwarded-host', 'x-forwarded-proto', 'x-forwarded-port']) {
      forwarded.headers.delete(name);
    }
    forwarded.headers.set('X-Forwarded-Proto', 'https');
    forwarded.headers.set('X-Forwarded-Host', url.host);
    forwarded.headers.set('X-Forwarded-Port', '443');
    const clientIp = request.headers.get('CF-Connecting-IP');
    if (clientIp) forwarded.headers.set('X-Forwarded-For', clientIp);
    // A single process preserves existing in-memory sessions while running.
    // Sessions still expire on sleep/restart; durable sessions are a launch follow-up.
    return env.CHEONGMO.getByName('production').fetch(forwarded);
  }
};

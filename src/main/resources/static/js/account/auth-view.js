import {enhanceCredentialFeedback} from '/credential-feedback.js';
export function createAuthView({state,dialog,$}) {
function authDialog(signup=false) {
  dialog(`${socialButtons()}<h2>${signup?'반가워요, 함께 준비해요.':'다시 만나 반가워요.'}</h2><p class="muted">${signup?'내 모임을 열거나 초대받은 청모에 참여해 보세요.':'로그인하고 우리의 약속을 이어가세요.'}</p><form id="auth-form" data-signup="${signup}">${signup?'<label>이름<input name="displayName" autocomplete="name" maxlength="30" required placeholder="모임에서 사용할 이름"></label>':''}${signup?'<label>복구 이메일<input name="recoveryEmail" type="email" autocomplete="email" required maxlength="254" placeholder="인증 메일을 받을 이메일"></label>':''}<label>아이디<input name="username" autocomplete="username" pattern="${signup?'[a-z0-9_\\-]{5,20}':'[a-z0-9_\\-]{3,30}'}" minlength="${signup?5:3}" maxlength="${signup?128:30}" required placeholder="${signup?'사용할 아이디':'가입한 아이디'}"></label><label>비밀번호<input name="password" type="password" autocomplete="${signup?'new-password':'current-password'}" minlength="8" maxlength="128" required placeholder="${signup?'사용할 비밀번호':'비밀번호'}"></label>${signup?'<label>비밀번호 확인<input name="passwordConfirmation" type="password" autocomplete="new-password" required maxlength="128"></label>':''}<p class="form-error" role="alert"></p><button class="button" type="submit">${signup?'가입하고 시작하기':'로그인'} ↗</button></form>${signup?'':'<div class="account-links"><a href="/account.html?mode=find-id">아이디 찾기</a><a href="/account.html?mode=reset-request">비밀번호 재설정</a></div>'}<button class="auth-switch" data-action="${signup?'login':'signup'}">${signup?'이미 계정이 있어요 · 로그인':'처음 오셨나요? · 회원가입'}</button>`);if(signup)enhanceCredentialFeedback($('#auth-form'));
}

function socialButtons() {
  const names={kakao:'카카오',google:'구글',naver:'네이버'};
  return `<div class="social-login"><p>호스트는 소셜 계정으로 가입·로그인할 수 있어요.</p>${state.providers.map(p=>p.enabled?`<a class="button secondary" href="/oauth2/authorization/${p.id}">${names[p.id]}로 계속하기</a>`:`<button class="button secondary" disabled>${names[p.id]} · 연동 준비 중</button>`).join('')}<p class="fine-print">${state.providers.some(p=>p.enabled)?'처음 로그인하면 호스트 계정이 만들어집니다.':'소셜 연동 키가 설정되면 활성화됩니다. 아래 계정으로도 시작할 수 있어요.'}</p></div>`;
}

return {authDialog};
}

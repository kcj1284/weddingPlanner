import {api as request} from '/js/shared/api-client.js';
import {escapeHtml as escape} from '/js/shared/view-helpers.js';
import {passwordError} from '/credential-policy.js';
import {enhanceCredentialFeedback} from '/credential-feedback.js';
const root=document.querySelector('#account-main');
const fragment=new URLSearchParams(location.hash.slice(1));
let token=fragment.get('reset')||fragment.get('verify')||'',mode=fragment.has('reset')?'reset':fragment.has('verify')?'verify':new URLSearchParams(location.search).get('mode')||'find-id';
if(token)history.replaceState(null,'','/account.html?mode='+mode);
function api(path,body){return request('/account/'+path,body?{method:'POST',body}:{});}
const links='<div class="account-links"><a href="/account.html?mode=find-id">아이디 찾기</a><a href="/account.html?mode=reset-request">비밀번호 재설정</a><a href="/">로그인으로 돌아가기</a></div>';
const emailInput='<label>이메일<input name="email" type="email" autocomplete="email" required maxlength="254" placeholder="인증한 이메일 주소"></label>';
function form(title,description,fields,button){root.innerHTML='<span class="eyebrow">YOUR ACCOUNT</span><h1>'+title+'</h1><p>'+description+'</p><form>'+fields+'<p class="form-error" role="alert"></p><button class="button" type="submit">'+button+'</button></form><p role="status" aria-live="polite"></p>'+links;}
async function render(){
  if(mode==='settings'){
    const status=await api('recovery-status');
    form('복구 이메일 설정',status.verified?'현재 인증된 이메일: <strong class="verified-email">'+escape(status.email)+'</strong><br>새 이메일 인증을 완료하기 전까지 기존 복구 이메일을 유지해요.':'이메일을 인증하면 아이디 찾기와 비밀번호 재설정을 사용할 수 있어요.',emailInput+'<label>현재 비밀번호<input name="password" type="password" required maxlength="128" autocomplete="current-password"></label>','인증 메일 보내기');
    const pending=sessionStorage.getItem('cheongmo.recoveryEmail');root.querySelector('[name=email]').value=pending||status.email;sessionStorage.removeItem('cheongmo.recoveryEmail');
    const sent=sessionStorage.getItem('cheongmo.recoveryNotice');if(sent){root.querySelector('[role=status]').textContent=sent;sessionStorage.removeItem('cheongmo.recoveryNotice');}
  }else if(mode==='verify'&&token){form('이메일 인증 완료하기','본인이 요청한 복구 이메일이라면 아래 버튼으로 인증을 완료해 주세요.','','이메일 인증 완료');}
  else if(mode==='reset'&&token){form('새 비밀번호 설정','변경 후 모든 기기에서 다시 로그인해야 해요.','<label>새 비밀번호<input name="password" type="password" required minlength="8" maxlength="128" autocomplete="new-password"></label><label>새 비밀번호 확인<input name="confirmation" type="password" required minlength="8" maxlength="128" autocomplete="new-password"></label>','비밀번호 변경');}
  else if(mode==='find-id'||mode==='reset-request'){form(mode==='find-id'?'아이디 찾기':'비밀번호 재설정',mode==='find-id'?'인증한 복구 이메일을 입력하면 아이디를 바로 확인할 수 있어요. 끝 4자리는 *로 표시됩니다.':'가입 후 인증한 복구 이메일을 입력해 주세요. 계정이 확인되면 해당 이메일로 안내를 보내드려요.',emailInput,mode==='find-id'?'아이디 확인하기':'재설정 메일 요청');}
  else{root.innerHTML='<h1>새 메일을 요청해 주세요.</h1><p>이 링크의 인증 정보를 찾을 수 없어요. 새 메일의 링크로 다시 시도해 주세요.</p>'+links;}
}
root.addEventListener('submit',async event=>{
  event.preventDefault();const formElement=event.target,fields=Object.fromEntries(new FormData(formElement));const button=formElement.querySelector('button'),error=formElement.querySelector('[role=alert]'),status=root.querySelector('[role=status]');button.disabled=true;error.textContent='';status.textContent='';
  try{
    if(mode==='reset'&&(fields.password!==fields.confirmation))throw new Error('두 비밀번호가 일치하지 않아요.');
    if(mode==='reset'&&passwordError(fields.password))throw new Error(passwordError(fields.password));
    const path=mode==='settings'?'verify-request':mode;
    const data=await api(path,mode==='verify'?{token}:mode==='reset'?{token,password:fields.password}:{email:fields.email.trim(),...(mode==='settings'?{password:fields.password}:{})});
    status.textContent=(data.maskedId?'찾은 아이디: '+data.maskedId+'\n':'')+data.message+(data.localMailbox?'\n개발 환경: 실제 발송 대신 artifacts/mailbox에 메일 파일을 생성합니다.':'');
    if(mode==='verify'||mode==='reset'){token='';formElement.hidden=true;root.querySelector('[role=status]').focus?.();}
  }catch(failure){error.textContent=failure.message;}
  finally{button.disabled=false;}
});
try{await render();if(mode==='reset'&&token)enhanceCredentialFeedback(root.querySelector('form'));}catch(error){root.innerHTML='<h1>계정 설정을 확인해 주세요.</h1><p>'+escape(error.message)+'</p><a class="button" href="/">청모에서 로그인하기 ↗</a>';}finally{root.setAttribute('aria-busy','false');}

export function enhanceCredentialFeedback(form){
  if(!form||form.dataset.liveCredentialRules)return;
  form.dataset.liveCredentialRules='true';
  const username=form.querySelector('[name="username"]'),password=form.querySelector('[name="password"]'),confirmation=form.querySelector('[name="passwordConfirmation"],[name="confirmation"]');
  const touched=new Set(),fields=[username,password,confirmation].filter(Boolean),feedback=new Map();
  for(const field of fields){
    const hint=document.createElement('div');hint.className='credential-feedback';hint.id='rules-'+(form.id||'account')+'-'+field.name;hint.hidden=true;hint.setAttribute('role','status');hint.setAttribute('aria-live','polite');hint.setAttribute('aria-atomic','true');
    field.closest('label').after(hint);field.setAttribute('aria-describedby',((field.getAttribute('aria-describedby')||'')+' '+hint.id).trim());feedback.set(field,hint);
  }
  function errors(field){
    const value=field.value,length=[...value].length;
    if(field===username){
      const messages=[];
      if(length<5||length>20)messages.push(length<5?`5~20자여야 해요. ${Math.max(0,5-length)}자 더 입력해 주세요.`:'20자 이내로 줄여 주세요.');
      if(value&&!/^[a-z0-9_-]+$/.test(value))messages.push('영문 소문자·숫자·밑줄(_)·하이픈(-)만 사용할 수 있어요.');
      return messages;
    }
    if(field===password)return length<8?[`8~64자여야 해요. ${8-length}자 더 입력해 주세요.`]:length>64?['64자 이내로 줄여 주세요.']:[];
    return !value?['같은 비밀번호를 한 번 더 입력해 주세요.']:value!==password.value?['비밀번호가 일치하지 않아요.']:[];
  }
  function update(field,force=false){
    const hint=feedback.get(field);if(!hint)return;
    const visible=force||touched.has(field)||document.activeElement===field||Boolean(field.value);
    hint.hidden=!visible;if(!visible)return;
    const problems=errors(field),empty=!field.value&&!touched.has(field);
    const state=empty?'neutral':problems.length?'invalid':'valid';
    hint.dataset.state=state;field.dataset.ruleState=state;field.setAttribute('aria-invalid',String(state==='invalid'));
    field.setCustomValidity(state==='invalid'?problems[0]:'');
    hint.replaceChildren();
    const heading=document.createElement('strong');heading.textContent=state==='valid'?'입력 규칙 충족':state==='invalid'?'확인 필요':'입력하면 확인해 드릴게요';hint.append(heading);
    const messages=state==='neutral'?[field===username?'소문자·숫자·_·-로 5~20자 입력해 주세요.':field===password?'8~64자로 입력해 주세요.':'위와 같은 비밀번호를 입력해 주세요.']:state==='invalid'?problems:[field===confirmation?'비밀번호가 일치해요.':field===username?'아이디 형식이 올바릅니다.':'비밀번호 규칙을 모두 충족했어요.'];
    const list=document.createElement('ul');for(const text of messages){const item=document.createElement('li');item.textContent=text;list.append(item);}hint.append(list);
  }
  function changed(event){if(!feedback.has(event.target))return;touched.add(event.target);update(event.target);if(event.target===password&&confirmation&&(touched.has(confirmation)||confirmation.value))update(confirmation);}
  form.addEventListener('input',changed);form.addEventListener('change',changed);
  form.addEventListener('focusin',event=>{if(feedback.has(event.target))update(event.target,true);});
  form.addEventListener('focusout',event=>{if(feedback.has(event.target)){touched.add(event.target);update(event.target,true);}});
  form.addEventListener('submit',()=>{for(const field of fields){touched.add(field);update(field,true);}});
  for(const field of fields)if(field.value)update(field);
}

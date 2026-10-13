const sdkUrl='https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
let sdkPromise;
const postcodeClass=()=>window.kakao?.Postcode||window.daum?.Postcode;
function loadSdk(){
  if(postcodeClass())return Promise.resolve(postcodeClass());
  if(sdkPromise)return sdkPromise;
  sdkPromise=new Promise((resolve,reject)=>{
    const script=document.createElement('script');script.src=sdkUrl;script.async=true;
    const finish=error=>{clearTimeout(timer);script.onload=script.onerror=null;if(error){script.remove();reject(error);}else resolve(postcodeClass());};
    const timer=setTimeout(()=>finish(new Error('주소 검색 연결이 지연되고 있어요. 다시 시도하거나 주소를 직접 입력해 주세요.')),15000);
    script.onload=()=>finish(postcodeClass()?null:new Error('주소 검색을 불러오지 못했어요. 주소를 직접 입력할 수 있어요.'));
    script.onerror=()=>finish(new Error('주소 검색에 연결하지 못했어요. 인터넷 연결을 확인하거나 직접 입력해 주세요.'));
    document.head.append(script);
  }).catch(error=>{sdkPromise=null;throw error;});
  return sdkPromise;
}

export function enhanceAddressSearch(){
  const input=document.querySelector('#editor-form [name="address"]');if(!input)return;
  const label=input.closest('label');
  input.setAttribute('aria-describedby','venue-address-hint');
  const controls=document.createElement('div');controls.className='venue-address-controls';
  controls.innerHTML='<button type="button" class="secondary" aria-expanded="false" aria-controls="venue-address-search">주소 검색</button><p id="venue-address-hint" class="hint">도로명·지번·건물명으로 검색하세요. 선택 후 상세주소를 덧붙일 수 있어요.</p>';
  const panel=document.createElement('section');panel.id='venue-address-search';panel.className='venue-address-search';panel.hidden=true;panel.setAttribute('aria-label','예식장 주소 검색');
  panel.innerHTML='<div class="venue-address-search-head"><strong>예식장 주소 찾기</strong><button type="button" class="secondary">검색 닫기</button></div><p class="hint" role="status"></p><div class="venue-address-frame"></div>';
  label.after(controls,panel);
  const button=controls.querySelector('button'),close=panel.querySelector('button'),message=panel.querySelector('[role="status"]'),frame=panel.querySelector('.venue-address-frame');
  let generation=0;
  function hide(){generation++;panel.hidden=true;button.setAttribute('aria-expanded','false');button.disabled=false;button.textContent='주소 검색';frame.replaceChildren();}
  close.addEventListener('click',()=>{hide();button.focus({preventScroll:true});});
  button.addEventListener('click',async()=>{
    const request=++generation;
    panel.hidden=false;button.setAttribute('aria-expanded','true');button.disabled=true;button.textContent='불러오는 중…';message.textContent='주소 검색을 불러오고 있어요.';
    try{
      const Postcode=await loadSdk();if(request!==generation||!panel.isConnected)return;
      frame.replaceChildren();message.textContent='주소를 검색한 뒤 도로명 또는 지번 주소를 선택하세요.';
      new Postcode({width:'100%',height:'100%',onresize:size=>{frame.style.height=Math.max(300,Number(size.height)||400)+'px';},oncomplete:result=>{
        if(request!==generation||!input.isConnected)return;
        const address=result.userSelectedType==='R'?result.roadAddress:result.jibunAddress;
        if(!address){message.textContent='주소를 선택해 주세요.';return;}
        input.value=address;input.dispatchEvent(new Event('input',{bubbles:true}));hide();input.focus();input.setSelectionRange(input.value.length,input.value.length);
      }}).embed(frame,{autoClose:false});
      // The SDK's about:blank wrapper inherits our CSP, so use a same-origin stylesheet.
      const wrapper=frame.querySelector('iframe');
      const styleWrapper=()=>{const wrapperDocument=wrapper?.contentDocument;if(!wrapperDocument||wrapperDocument.querySelector('#address-frame-style'))return;const stylesheet=wrapperDocument.createElement('link');stylesheet.id='address-frame-style';stylesheet.rel='stylesheet';stylesheet.href=location.origin+'/invitation-address-frame.css';wrapperDocument.head.append(stylesheet);};
      wrapper?.addEventListener('load',styleWrapper);styleWrapper();
      panel.scrollIntoView({block:'start',behavior:'smooth'});
    }catch(error){if(request===generation&&panel.isConnected)message.textContent=error.message;}
    finally{if(request===generation){button.disabled=false;button.textContent='주소 검색';}}
  });
}

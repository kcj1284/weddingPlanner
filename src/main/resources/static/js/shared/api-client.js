let csrfToken=null;
export async function csrf() { csrfToken = await (await fetch('/api/csrf')).json(); }
export async function api(path, options={}) {
  const method=options.method||'GET'; const headers={...(options.headers||{})};
  if(method!=='GET') { if(!csrfToken) await csrf(); headers[csrfToken.headerName]=csrfToken.token; }
  let body=options.body;
  if(body && !(body instanceof URLSearchParams)) { headers['Content-Type']='application/json'; body=JSON.stringify(body); }
  const response=await fetch('/api'+path,{...options,method,headers,body});
  if(response.status===204) return null;
  const data=await response.json().catch(()=>({message:'잠시 후 다시 시도해 주세요.'}));
  if(!response.ok) { const error=new Error(data.message||'요청을 처리하지 못했어요.'); error.status=response.status; throw error; }
  return data;
}

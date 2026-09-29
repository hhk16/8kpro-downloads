const http=require('http');
const fs=require('fs');
const path=require('path');
const root=__dirname;
const port=Number(process.env.PORT||3000);
const types={'.html':'text/html; charset=utf-8','.js':'application/javascript; charset=utf-8','.webmanifest':'application/manifest+json; charset=utf-8','.json':'application/json; charset=utf-8','.png':'image/png','.svg':'image/svg+xml','.txt':'text/plain; charset=utf-8'};
http.createServer((req,res)=>{
  let p=decodeURIComponent((req.url||'/').split('?')[0]);
  if(p==='/') p='/index.html';
  const file=path.normalize(path.join(root,p));
  if(!file.startsWith(root)){res.writeHead(403);return res.end('Forbidden');}
  fs.readFile(file,(err,data)=>{
    if(err){
      fs.readFile(path.join(root,'index.html'),(e,fallback)=>{
        if(e){res.writeHead(404);return res.end('Not found');}
        res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-cache'});
        res.end(fallback);
      });
      return;
    }
    const ext=path.extname(file).toLowerCase();
    const headers={'Content-Type':types[ext]||'application/octet-stream'};
    headers['Cache-Control']=(file.endsWith('index.html')||file.endsWith('service-worker.js')||file.endsWith('manifest.webmanifest'))?'no-cache':'public, max-age=86400';
    res.writeHead(200,headers);res.end(data);
  });
}).listen(port,'0.0.0.0',()=>console.log('100 PUSH listening on '+port));

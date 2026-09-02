# TLS 证书放置说明

本目录放生产 HTTPS 证书，**已被 .gitignore 忽略，私钥不会进仓库**。

证书就绪后（见 [docs/deploy-online.md](../docs/deploy-online.md) 第 4 节），把文件命名为：

```
deploy/certs/fullchain.pem    # 证书链（阿里云/腾讯云免费证书 nginx 版的 .pem）
deploy/certs/privkey.key      # 私钥（.key）
```

若文件名不同，同步修改 `deploy/nginx-https.conf` 里的两行：

```nginx
ssl_certificate     /etc/nginx/certs/fullchain.pem;
ssl_certificate_key /etc/nginx/certs/privkey.key;
```

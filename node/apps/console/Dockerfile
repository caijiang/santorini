FROM nginx:alpine

COPY dist/ /usr/share/nginx/html

# 可选: Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

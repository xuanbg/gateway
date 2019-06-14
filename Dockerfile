# 使用的基础镜像
FROM insight/ubuntu:jre8_512

# 作者信息
MAINTAINER Brian "brian.xan@gmail.com"

ADD target/*.jar /usr/local/insight/app.jar

EXPOSE 8761
ENTRYPOINT ["/usr/local/insight/start.sh"]
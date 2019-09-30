# 使用的基础镜像
FROM insight/ubuntu:jre8

# 作者信息
MAINTAINER Brian "brian.xan@gmail.com"

ADD target/*.jar /usr/local/insight/app.jar

ENTRYPOINT ["/usr/local/insight/start.sh"]
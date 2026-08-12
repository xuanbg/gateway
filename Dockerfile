# 使用的基础镜像
FROM insight/jdk:17

ADD *.jar /root/app.jar
ENTRYPOINT ["/root/start.sh"]

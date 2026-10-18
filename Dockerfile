# 使用预装 Maven 和 JDK21 的镜像
FROM maven:3.9-amazoncorretto-21
WORKDIR /app

# 只复制必要的源代码和配置文件
COPY pom.xml .
COPY src ./src

# 使用 Maven 执行打包
RUN mvn clean package -DskipTests

# 暴露应用端口
EXPOSE 8123

# 使用生产环境配置启动应用（文件编码 UTF-8；控制台编码跟随运行环境）
CMD ["java", "-Dfile.encoding=UTF-8", "-jar", "/app/target/ecom-aftersales-agent-0.0.1-SNAPSHOT.jar", "--spring.profiles.active=prod"]
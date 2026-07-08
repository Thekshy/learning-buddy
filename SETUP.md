# 一次性工具链安装指南

> macOS (Apple Silicon) · 给团队成员和评审现场用

## 必须

```bash
# JDK 21
brew install openjdk@21
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"

# Maven 3.9+
brew install maven

# Node 20+(自带)
node -v
```

## 验证

```bash
java -version    # 应输出 21.x
mvn -version     # 应输出 Apache Maven 3.9.x
```

## 备选:如果 brew 镜像访问失败

```bash
# 1) 换 brew 镜像
export HOMEBREW_API_DOMAIN="https://mirrors.aliyun.com/homebrew-api"
export HOMEBREW_BOTTLE_DOMAIN="https://mirrors.aliyun.com/homebrew-bottles"
brew install openjdk@21 maven

# 2) 仍然失败,直接从官网下便携版(无需安装)
curl -fsSL -o /tmp/jdk21.tar.gz \
  "https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_linux-x64_bin.tar.gz"
mkdir -p ~/opt/jdk21 && tar -xzf /tmp/jdk21.tar.gz -C ~/opt/jdk21 --strip-components=1
export JAVA_HOME=~/opt/jdk21
export PATH=$JAVA_HOME/bin:$PATH
```

## 跑项目

```bash
cd learning-buddy
cp .env.example .env
# 编辑 .env 填 LLM_API_KEY

cd backend && mvn spring-boot:run &
cd frontend && npm install && npm run dev
# 浏览器打开 http://localhost:3000
```

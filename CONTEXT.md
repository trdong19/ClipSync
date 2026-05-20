# CONTEXT.md

项目领域知识文档，供 AI 技能读取以理解项目。

## 核心概念

- **ClipSync**: 跨设备剪贴板同步工具
- **MQTT Broker**: 消息中转服务器 (Mosquitto)，负责设备间通信
- **Topic**: MQTT 主题，同一 topic 下的设备共享剪贴板
- **Broker**: MQTT 服务器，接收并转发消息
- **Client**: 连接到 broker 的设备 (Windows/Linux/Android)

## 架构

- 后端: Python + FastAPI + paho-mqtt
- Android: Kotlin + Paho MQTT + Material Design 3
- 通信: MQTT 协议，JSON 消息格式
- 消息格式 (Windows): `{"id": "uuid", "name": "hostname", "value": "text"}`
- 消息格式 (Android): `{"type": "clip", "content": "text", "timestamp": 123}`

# WHU TOP Developer · 一期正式版

全国高校开源项目协作站内流程。学生使用 `*.edu.cn` 邮箱和昵称，发布方使用 `*.whu.edu.cn` 邮箱并实名。形态参考开源之夏的结构化项目、申请书和结项 PR，不做组委会二审、支付、电子签和 GitHub App。

需求说明见 [docs/whu-top-developer-mvp.md](docs/whu-top-developer-mvp.md)。

本机用 Docker Compose 把 MySQL、API 和 Nginx 静态站一起拉起。生产部署是 Nginx 托管前端静态文件、单独运行 Spring Boot jar、再接独立 MySQL 8，示例配置在 [deploy/nginx.conf.example](deploy/nginx.conf.example)。时区固定为 Asia/Shanghai。

## 本地运行

需要 Docker，以及 `docker compose`。

```bash
docker compose up --build
```

如果之前跑过演示版，先清掉旧库：

```bash
docker compose down -v
docker compose up --build
```

- 页面：<http://localhost:5173>
- 接口：<http://localhost:8080>
- 健康检查：<http://localhost:8080/api/health>

没有配置 `SPRING_MAIL_HOST` 时，验证码和催办邮件写在 API 日志里。开发态接口也会把验证码放在发送结果的 `devCode` 字段，注册页会直接显示。要关掉它，把环境变量 `APP_OTP_EXPOSE` 设为 `false`。

## 种子账号

| 角色 | 邮箱 | 密码 | 说明 |
| --- | --- | --- | --- |
| 管理员 | `admin@whu.edu.cn` | `admin123456` | 用户、任务、催办、SLA |
| 发布方 | `li.teacher@cs.whu.edu.cn` | `publisher123` | 实名李明，已上架开源协作项目 |
| 学生 | `wang.student@stu.edu.cn` | `student123` | 昵称王同学，初始等级 L2 |

## 走一遍

1. 用学生账号登录，打开任务广场里的一条任务。
2. 阅读项目要求后，在「项目申请」区域上传不超过 5 MB 的 PDF 申请书。每个任务只能投递一次，发布方在审阅页下载申请书并审批。
3. 退出后用发布方登录，进入「审阅」。可以约面试，再点「通过并入项」。通过后直接进入开发，工作台展示原申请书；不再重复需求确认、设计评审或开发交付审批，成果统一在结项时验收。
4. 学生进入「工作台」：备选项目可看发布方任务详情；已接项目内分「项目详情 / 推进与周报 / 结项验收」页签分别操作。
5. 发布方在同一项目里查看申请书、给周报打 1–5 分，并点「通过结项」。
6. 管理员可以把「SLA」里的未审天数改成 `0`，回到「催办队列」点「立即扫描」。超过阈值的未审申请会给发布方发站内通知和邮件日志，并在达到升级天数后进入管理队列。默认是 7 天催办、14 天升级。定时任务每分钟扫一次。
7. 管理员可以在「用户」里改学生的 L1–L5，在「任务」里改上下架。关键操作记在催办页底部的审计里。

自助注册：学生邮箱必须以 `.edu.cn` 结尾，发布方必须以 `.whu.edu.cn` 结尾。管理员不能自助注册。

## 和之前的演示相比

演示版只有固定账号、两条任务和一条点击路径。这一版补上了：

- 邮箱验证码注册、JWT、学生昵称 / 发布方实名、管理员
- 结构化任务、PRD 与技术文档、难度、名额、上下架
- 申请书、通过 / 拒绝、可选面试、任务内私信
- 7 日未审催办，可升级管理队列
- 入项后的申请书展示、周报评分、结项报告和手填 PR
- 人工 L1–L5 和管理后台

仍然不做支付、电子签、平台二审、GitHub Webhook、积分自动评级、社区信息流、App 和小程序。

## 目录

```
frontend/   Vue 3 + Vite + TypeScript
backend/    Spring Boot 3，Flyway 迁移到一期正式表结构
deploy/     生产 Nginx 示例
docs/       需求与建设文档
```

申请书 PDF 存储在数据库中，下载接口校验学生本人、对应发布方或管理员权限；公开项目文档的上传接口不用于申请书。历史文字申请书仍可查看。

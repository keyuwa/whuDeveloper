# WHU TOP Developer · 第一期演示

一条可以点完的最小路径：登录 → 看任务 → 提交申请 → 发布者录取 → 学生入学 → 周报 → 用 Pull Request 链接结项。

页面在 <http://localhost:5173>，接口在 <http://localhost:8080>。

产品说明见 [需求与建设文档](docs/whu-top-developer-mvp.md)（对应在线文档 <https://whu-top-developer-mvp-daw6.pages.grok.bot/>）。本仓库只跑其中一条演示路径，不含教务校验、邮件验证码、催办和等级后台。

## 怎么跑

需要 Docker，以及能使用 `docker compose`。

```bash
docker compose up --build
```

第一次会下载 MySQL、编译 Spring Boot、构建前端，需要等几分钟。三个服务都起来后：

1. 打开 <http://localhost:8080/api/health>，看到 `{"status":"ok"}`。
2. 打开 <http://localhost:5173>。

不想占用当前终端时：

```bash
docker compose up --build -d
docker compose logs -f api
```

可选：把 `.env.example` 复制为 `.env` 再改数据库密码或 JWT 密钥。不复制也能启动，compose 里有同样的默认值。

停掉并清掉演示数据：

```bash
docker compose down -v
```

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| web | http://localhost:5173 | 页面。浏览器只访问这一个地址，`/api` 由 Nginx 转到 api |
| api | http://localhost:8080 | Spring Boot |
| mysql | localhost:3306 | 库 `whu_developer`，用户 `whu` / 密码 `whu`，root 密码 `root` |

## 预置账号

密码只用于本机演示。

| 角色 | 姓名 | 用户名 | 密码 |
| --- | --- | --- | --- |
| 发布者 | 李老师 | `publisher` | `publisher123` |
| 学生 | 王同学 | `student` | `student123` |

登录页有两个按钮，会直接用对应账号进入。

## 预置任务

两条任务都由李老师发布，状态为开放：

1. **数据结构课程助教**（课程助教 / TA）
2. **校园开发者门户实习**（开发实习 / INTERN）

下面用其中任意一条走通。助教和实习的页面相同，只是类型标签不同。

## 点一遍

1. 打开 <http://localhost:5173>。
2. 点「学生 · 王同学」，或使用 `student` / `student123` 登录。
3. 在「开放任务」里点进「数据结构课程助教」或「校园开发者门户实习」。
4. 点「填写申请」。申请说明已经写好一段，可直接点「提交申请」。
5. 右上角「退出」。点「发布者 · 李老师」，或使用 `publisher` / `publisher123` 登录。
6. 打开刚才那条任务，点「审核申请」，再点「录取」。
7. 再次退出，用学生账号登录。打开同一条任务的申请页，点「确认入学」。
8. 进入项目页。周报内容已填好，点「提交周报」。项目状态变为「进行中」。
9. 结项区已填好 PR 链接 `https://github.com/keyuwa/whuDeveloper/pull/1` 和结项说明，点「提交结项」。
10. 页面显示「已结项」，PR 链接可以点开。退出后用发布者登录，打开「项目」，能看到同一份周报和结项结果。发布者不能代写周报或结项。

结项前必须先有至少一份周报。同一任务每个学生只能申请一次，同一项目的同一周次只能交一份周报。

## 页面

1. 登录 `/login`
2. 任务列表与详情 `/tasks`、`/tasks/:id`
3. 申请与录取 `/tasks/:id/application`（学生提交说明；发布者录取；学生确认入学）
4. 周报与结项 `/projects`、`/projects/:id`

## 这次没做

教务身份校验、邮箱验证码、SLA 定时任务、面试、L1–L5 等级界面、文件上传、生产环境 Nginx。

## 目录

```
frontend/     Vue 3 + Vite + TypeScript
backend/      Spring Boot 3，包：auth、task、application、project、report、completion
              Flyway：users、tasks、applications、projects、weekly_reports、completions
docker-compose.yml
.env.example
```

主要接口：

| 方法 | 路径 | 谁来用 |
| --- | --- | --- |
| POST | `/api/auth/login` | 所有人 |
| GET | `/api/tasks`、`/api/tasks/{id}` | 登录用户 |
| POST | `/api/tasks/{id}/applications` | 学生提交申请 |
| GET | `/api/tasks/{id}/applications` | 发布者查看申请 |
| POST | `/api/applications/{id}/accept` | 发布者录取 |
| POST | `/api/applications/{id}/enroll` | 学生入学，生成项目 |
| GET | `/api/projects`、`/api/projects/{id}` | 该项目的学生或发布者 |
| POST | `/api/projects/{id}/reports` | 学生提交周报 |
| POST | `/api/projects/{id}/completion` | 学生提交 PR 链接并结项 |

## 不用 Docker 时

本机需要 JDK 21、Maven、Node.js 20+，以及一个 MySQL 8。先建库和用户（与 `.env.example` 一致），再：

```bash
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

前端开发服务器会把 `/api` 代理到 `http://localhost:8080`。页面仍是 <http://localhost:5173>。

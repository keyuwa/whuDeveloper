---
name: whudev-publish-task
description: >-
  Publishes a WHU Dev task for a teacher or 发布方 (PUBLISHER): 助教 TA, 开发 Intern,
  科研 RA, or 项目 TEAM. Use when the user asks to publish, post, or create a task,
  发布任务, or 发一个助教、实习、科研、项目. Only publishing; do not review applications or message students.
---

# 发布 WHU Dev 任务

只帮发布方（老师账号，角色 `PUBLISHER`）创建并发布任务。不审阅申请、不和学生沟通、不改已发布任务，除非同一次发布校验失败需要改正后再发。

## 配置

读取环境变量，缺了就问用户，不要把密码或 token 写进本技能：

- `WHUDEV_EMAIL`、`WHUDEV_PASSWORD`：发布方账号

登录：

```bash
curl -s -X POST "$WHUDEV_BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"'"$WHUDEV_EMAIL"'","password":"'"$WHUDEV_PASSWORD"'"}'
```

之后每次请求带 `Authorization: Bearer <token>`。先 `GET /api/v1/me`，`role` 不是 `PUBLISHER` 就停下来说明需要发布方账号。

错误体是 `{ "message": "..." }`，把 `message` 告诉用户。

## 发布前

1. 确认类型：`TA` 助教、`INTERN` 开发、`RA` 科研、`TEAM` 项目。
2. 缺的必填项先问用户，不要编造补助、名额、方向或文档。
3. 方向、难度、语言、学期、岗位都从字典取 `value`：`GET /api/v1/dicts/{type}`。类型见下表。用户给的是中文时，用 `label` 匹配后再提交 `value`。
4. 把将要提交的标题、类型、方向、补助、名额复述给用户，得到同意后再 `POST`。

`POST {BASE}/api/v1/publisher/tasks?publish=true`

公共必填：`title`、`roleType`、`background`、`stipendText`。

| 类型 | 另外必填 | 字典 |
| --- | --- | --- |
| `INTERN` | `techField`、`difficulty`、`programmingLanguages`、`techStack`、`durationWeeks`（1–52）、`estimatedHours`（1–40）、`outputRequirement`、`techRequirement`、`quota`、`prdFileUrl` | `TECH_FIELD`、`DIFFICULTY`、`PROGRAMMING_LANGUAGE` |
| `TA` | `scheduleType`、`educationLevels`（逗号分隔）、`estimatedHours`、`outputRequirement`、`techRequirement`、`quota` | `TA_SCHEDULE`、`STUDENT_IDENTITY` |
| `RA` | `techField` 填研究方向。名额默认 1。产出和能力要求可空 | `RESEARCH_DIRECTION` |
| `TEAM` | `positions`：每项 `direction`（岗位字典值）、`quota`（至少 1）、可选 `requirement`。至少 1 个岗位 | `TEAM_POSITION` |

开发任务的需求 PDF：`POST /api/v1/files`，表单字段名 `file`，只接受 PDF。把返回的 `url`、`fileName` 填进 `prdFileUrl`、`prdFileName`。没有这份文件就不要发开发任务。

科研和项目不要传编程语言；助教不要传技术领域。这些字段服务端会自己填默认值。

## 成功之后

用返回的 `id`、`publicCode`、`title`、`status` 告诉用户。`status` 应为 `OPEN`。校验失败时只改正报错指出的字段再提交，不要改去审阅或私信。

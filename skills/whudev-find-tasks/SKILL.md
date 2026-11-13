---
name: whudev-find-tasks
description: >-
  Searches open WHU Dev tasks for a student: 助教 TA, 开发 Intern, 科研 RA, and 项目.
  Use when the user asks to find, search, browse, or recommend campus tasks,
  找任务, 任务广场, or WHU Dev openings. Only finding; do not apply, message, or publish.
---

# 找 WHU Dev 任务

只帮学生在已发布任务里查找和读详情。不报名、不发私信、不发布任务。

## 配置

先读环境变量 `WHUDEV_BASE_URL`。

查找公开任务不需要登录。

## 查找

`GET {BASE}/api/v1/tasks`

| 参数 | 说明 |
| --- | --- |
| `roleType` | `TA` 助教、`INTERN` 开发、`RA` 科研、`TEAM` 项目 |
| `techField` | 技术领域或研究方向，必须是字典 `value` |
| `difficulty` | 仅开发任务，字典 `DIFFICULTY` |
| `programmingLanguage` | 仅开发任务，字典 `PROGRAMMING_LANGUAGE` |
| `scheduleType` | 仅助教，字典 `TA_SCHEDULE` |
| `durationMin` / `durationMax` | 周期周数 |
| `hoursMin` / `hoursMax` | 每周小时 |
| `page` | 从 0 开始 |
| `size` | 1–50，默认 20 |

接口没有关键词参数。用户用标题、编号或描述找时，带上能确定的筛选，再在返回的 `items` 里对 `title`、`publicCode`、`techField`、`background` 做不区分大小写匹配。`hasMore` 为 true 时继续翻页，最多 5 页；还没找全就说明只看了前 5 页。

字典：`GET {BASE}/api/v1/dicts/{type}`。用户说的是中文标签时，用返回项的 `label` 对上 `value`，请求里传 `value`。常用类型：`TECH_FIELD`、`RESEARCH_DIRECTION`、`DIFFICULTY`、`PROGRAMMING_LANGUAGE`、`TA_SCHEDULE`。

单条详情：`GET {BASE}/api/v1/tasks/{id}`。只对已发布任务可读。

## 怎么回答

按用户的原话筛，不要自行放宽类型。每条给出标题、类型、方向或领域、补助、周期或学期、发布方、`id` 和 `publicCode`。用户点名某一条时再拉详情，补上背景、产出要求和名额。

失败时读 JSON 的 `message` 并原样说明。用户接着要报名或联系发布方时，说明这个技能只负责寻找。

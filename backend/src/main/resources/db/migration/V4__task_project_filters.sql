ALTER TABLE tasks
  ADD COLUMN tech_field VARCHAR(64) NOT NULL DEFAULT '全栈应用' AFTER role_type,
  ADD COLUMN programming_languages VARCHAR(300) NOT NULL DEFAULT '' AFTER tech_stack,
  ADD COLUMN support_languages VARCHAR(64) NOT NULL DEFAULT '中文' AFTER programming_languages;

UPDATE tasks SET
  tech_field = CASE role_type
    WHEN 'TA' THEN '开发工具'
    WHEN 'INTERN' THEN '全栈应用'
    WHEN 'RA' THEN '系统与底层'
    ELSE '全栈应用'
  END,
  programming_languages = REPLACE(REPLACE(tech_stack, ' · ', ','), ' / ', ','),
  support_languages = '中文',
  role_type = 'PROJECT'
WHERE role_type IN ('TA', 'INTERN', 'RA');

UPDATE tasks SET
  title = '校园组件库与设计系统',
  role_type = 'PROJECT',
  tech_field = '前端开发',
  difficulty = 'L3',
  background = '沉淀可复用的校园业务组件与设计规范，降低各院系门户重复建设成本。',
  tech_stack = 'Vue 3, TypeScript, Vite',
  programming_languages = 'TypeScript,Vue,CSS',
  support_languages = '中文',
  output_requirement = '组件文档站点、至少 6 个可复用组件、示例仓库。',
  tech_requirement = '熟悉 Vue 3 与组件化设计，能编写基础文档。',
  duration_weeks = 8,
  estimated_hours = 10,
  learning_materials = 'Vue 3 官方文档；现有门户代码。',
  stipend_text = '开源贡献补贴按实验室公示，仅展示。',
  prd_md = '## 背景\n校园前端组件分散，缺乏统一规范。\n## 目标\n输出可安装的组件库与文档站。\n## 验收\n文档可访问，组件有单测/示例。',
  tech_doc_md = '## 技术说明\n基于 Vue 3 + TypeScript，文档站可用 VitePress。',
  quota = 2
WHERE id = 1;

UPDATE tasks SET
  title = '开发者文档站点生成器',
  role_type = 'PROJECT',
  tech_field = '全栈应用',
  difficulty = 'L2',
  background = '为实验室项目提供一键生成文档站的能力，支持 Markdown 与多语言目录。',
  tech_stack = 'Node.js, Markdown, React',
  programming_languages = 'JavaScript,TypeScript,Markdown',
  support_languages = '中英双语',
  output_requirement = 'CLI 或 Web 入口、中英文档示例、部署说明。',
  tech_requirement = '能处理 Markdown 解析与静态站点构建。',
  duration_weeks = 6,
  estimated_hours = 8,
  learning_materials = 'MDX / VitePress / Docusaurus 任选其一。',
  stipend_text = '开源贡献补贴按实验室公示，仅展示。',
  prd_md = '## 背景\n项目文档常滞后。\n## 目标\n提供轻量文档站脚手架。\n## 验收\n示例站可部署，支持中英切换。',
  tech_doc_md = '## 技术说明\nNode 工具链 + 静态导出。',
  quota = 2
WHERE id = 2;

UPDATE tasks SET
  title = '代码评审助手插件',
  role_type = 'PROJECT',
  tech_field = '人工智能',
  difficulty = 'L4',
  background = '基于大模型辅助 Pull Request 评审，输出风格、风险与测试建议。',
  tech_stack = 'Python, LLM API, GitHub',
  programming_languages = 'Python',
  support_languages = 'English',
  output_requirement = '可运行的评审机器人、提示词与评测样例、使用文档。',
  tech_requirement = '熟悉 Python 与 REST API；了解 GitHub webhook 更佳。',
  duration_weeks = 12,
  estimated_hours = 12,
  learning_materials = 'GitHub Apps 文档；所选模型 API 文档。',
  stipend_text = '开源贡献补贴按实验室公示，仅展示。',
  prd_md = '## 背景\n人工评审成本高。\n## 目标\n自动生成可复核的评审意见。\n## 验收\n对样例 PR 给出结构化报告。',
  tech_doc_md = '## 技术说明\nPython 服务 + LLM；可先做 CLI。',
  quota = 1
WHERE id = 3;

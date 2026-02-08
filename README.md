# CalendarTask
# CalendarTask 项目介绍 / Project Introduction

## 中文版 (Chinese Version)

**日程任务 (CalendarTask)** 是一款基于开源 Fossify Calendar 项目深度定制与研发的日程管理应用。由 **Antigravity** 独立完成开发与功能增强。

### 核心功能
*   **任务状态快速切换**：在日历视图中双击任意日程或任务，即可快速切换其完成状态。
*   **多级日程体系**：创新的层级日程管理系统。通过在描述字段添加 `#tag#` 格式的标签，您可以轻松构建复杂的任务树。
    *   **灵活层级**：起始处的 `#` 数量决定任务级别（从 L1 到 L4+）。
    *   **智能关联**：标签内容用于精准匹配上级日程，实现复杂任务的父子关系绑定。
    *   **示例**：
        *   `#tag1#`（1级日程）
        *   `##tag1#`（2级日程，上级为 tag1）
        *   `##tag1#tag2#`（2级日程，定义上级为 tag1 的同时也作为 tag2 的父级）
        *   `###tag1#tag2#`（3级日程，上级为 tag2）

### 联系与支持
如果您有任何建议或需要帮助，欢迎通过以下方式联系：
*   **电子邮箱**：joysky777@gmail.com
*   **微信**：gohorizon
*   **捐赠支持**：您的支持是我持续改进的动力。 [PayPal 捐赠](https://paypal.me/joysky77)

---

## English Version

**CalendarTask** is a schedule management application based on the open-source Fossify Calendar project, extensively customized and enhanced by **Antigravity**.

### Core Features
*   **Quick Status Toggling**: Simply double-click any event or task in the calendar view to toggle its completion status instantly.
*   **Multi-level Schedules**: An innovative hierarchical scheduling system. By adding `#tag#` style tags in the description field, you can easily build complex task structures.
    *   **Flexible Levels**: The number of leading `#` characters determines the level of the schedule (e.g., L1-L4+).
    *   **Smart Linking**: The content within the tags is used to precisely match and link to the parent schedule, enabling complex parent-child relationships.
    *   **Examples**:
        *   `#tag1#` (Level 1)
        *   `##tag1#` (Level 2, parent is tag1)
        *   `##tag1#tag2#` (Level 2, parent is tag1, and also defines tag2 as a parent for others)
        *   `###tag1#tag2#` (Level 3, parent is tag2)

### Contact & Support
If you have any suggestions or need assistance, feel free to reach out:
*   **Email**: joysky777@gmail.com
*   **WeChat**: gohorizon
*   **Support & Donation**: Your support keeps the development going! [Donate via PayPal](https://paypal.me/joysky77)

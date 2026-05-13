export interface QaKnowledgeRegressionCase {
  id: string
  prompt: string
  mustUseFetch: boolean
}

export const qaKnowledgeRegressionCases: QaKnowledgeRegressionCase[] = [
  {
    id: 'R01',
    prompt: '请基于产品文档回答：中国移动视频彩铃实时决策节点技术要求的版本号是多少？',
    mustUseFetch: true,
  },
  {
    id: 'R02',
    prompt: '请基于产品文档回答：中国移动视频彩铃实时决策节点技术要求的用户范围章节叫什么？',
    mustUseFetch: true,
  },
  {
    id: 'R03',
    prompt: '请基于产品文档回答：中国移动视频彩铃实时决策节点技术要求中“集中播控功能架构”对应的架构名称是什么？',
    mustUseFetch: true,
  },
  {
    id: 'R04',
    prompt: '请基于产品文档回答：中国移动视频彩铃实时决策节点技术要求中维护要求包含哪类日志要求？',
    mustUseFetch: true,
  },
  {
    id: 'R05',
    prompt: '请基于产品文档回答：中国移动视频彩铃实时决策节点技术要求中业务流程的 7.7 章节是什么？',
    mustUseFetch: true,
  },
  {
    id: 'R06',
    prompt: '请基于 RBT GW 数据库系统设计说明书回答：文档发布日期是什么？',
    mustUseFetch: true,
  },
  {
    id: 'R07',
    prompt: '请基于 RBT GW 数据库系统设计说明书回答：读者对象包含哪类人员？',
    mustUseFetch: true,
  },
  {
    id: 'R08',
    prompt: '请基于 RBT GW 数据库系统设计说明书回答：RBT GW 版本信息表的表名是什么？',
    mustUseFetch: true,
  },
]

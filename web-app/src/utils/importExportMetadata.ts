import { FieldMetadata, ResourceImportMetadata } from '../types/importExport'
import type { ImportType } from '../types/importExport'

export const IMPORT_METADATA: Record<ImportType, ResourceImportMetadata> = {
    ClusterTypes: {
        resourceType: 'ClusterTypes',
        sheetName: 'Cluster Types',
        descriptionSheetName: '字段说明',
        fields: [
            { name: 'name', labelKey: 'field_clusterTypes_name', enLabel: 'Cluster Type Name', zhLabel: '集群类型名称', required: true, validation: { type: 'string', maxLength: 100 } },
            { name: 'code', labelKey: 'field_clusterTypes_code', enLabel: 'Cluster Type Code', zhLabel: '集群类型代码', required: true, validation: { type: 'string', maxLength: 50 } },
            { name: 'description', labelKey: 'field_clusterTypes_description', enLabel: 'Description', zhLabel: '描述', required: false, validation: { type: 'string', maxLength: 500 } },
            { name: 'typeColor', labelKey: 'field_clusterTypes_typeColor', enLabel: 'Color', zhLabel: '标识颜色', required: false, validation: { type: 'string' } },
            { name: 'knowledge', labelKey: 'field_clusterTypes_knowledge', enLabel: 'Knowledge', zhLabel: '常识', required: false, validation: { type: 'string' } },
            { name: 'clusterMode', labelKey: 'field_clusterTypes_clusterMode', enLabel: 'Cluster Mode', zhLabel: '集群模式（可选：Peer/Primary-Backup）', required: false, validation: { type: 'enum', enumValues: ['Peer', 'Primary-Backup'] } },
            { name: 'commandPrefix', labelKey: 'field_clusterTypes_commandPrefix', enLabel: 'Command Prefix', zhLabel: '命令前缀', required: false, validation: { type: 'string' } },
            { name: 'envVariables', labelKey: 'field_clusterTypes_envVariables', enLabel: 'Environment Variables', zhLabel: '环境变量', required: false, validation: { type: 'array', separator: ';' } },
        ],
        sampleData: [
            {
                name: 'Kubernetes Cluster',
                code: 'k8s',
                description: 'Kubernetes container orchestration cluster',
                typeColor: '#FF6B6B',
                knowledge: 'container-orchestration',
                clusterMode: 'Peer',
                commandPrefix: 'kubectl',
                envVariables: 'KUBECONFIG=/etc/kubernetes/config;CLUSTER_NAME=prod',
            },
        ],
    },
    BusinessTypes: {
        resourceType: 'BusinessTypes',
        sheetName: 'Business Types',
        descriptionSheetName: '字段说明',
        fields: [
            { name: 'name', labelKey: 'field_businessTypes_name', enLabel: 'Business Type Name', zhLabel: '业务类型名称', required: true, validation: { type: 'string', maxLength: 100 } },
            { name: 'code', labelKey: 'field_businessTypes_code', enLabel: 'Business Type Code', zhLabel: '业务类型代码', required: true, validation: { type: 'string', maxLength: 50 } },
            { name: 'description', labelKey: 'field_businessTypes_description', enLabel: 'Description', zhLabel: '描述', required: false, validation: { type: 'string', maxLength: 500 } },
            { name: 'typeColor', labelKey: 'field_businessTypes_typeColor', enLabel: 'Color', zhLabel: '标识颜色', required: false, validation: { type: 'string' } },
            { name: 'knowledge', labelKey: 'field_businessTypes_knowledge', enLabel: 'Knowledge', zhLabel: '常识', required: false, validation: { type: 'string' } },
        ],
        sampleData: [
            {
                name: 'Web Application',
                code: 'web-app',
                description: 'Web application service',
                typeColor: '#6366f1',
                knowledge: 'web-services',
            },
        ],
    },
    HostGroups: {
        resourceType: 'HostGroups',
        sheetName: 'Host Groups',
        descriptionSheetName: '字段说明',
        fields: [
            { name: 'name', labelKey: 'field_hostGroups_name', enLabel: 'Host Group Name', zhLabel: '主机组名称', required: true, validation: { type: 'string', maxLength: 100 } },
            { name: 'code', labelKey: 'field_hostGroups_code', enLabel: 'Host Group Code', zhLabel: '主机组代码', required: false, validation: { type: 'string', maxLength: 50 } },
            { name: 'parentGroup', labelKey: 'field_hostGroups_parentGroup', enLabel: 'Parent Group', zhLabel: '父主机组', required: false, validation: { type: 'string' } },
            { name: 'description', labelKey: 'field_hostGroups_description', enLabel: 'Description', zhLabel: '描述', required: false, validation: { type: 'string', maxLength: 500 } },
            { name: 'enabled', labelKey: 'field_hostGroups_enabled', enLabel: 'Enabled', zhLabel: '启用状态（可选：true/false）', required: false, validation: { type: 'enum', enumValues: ['true', 'false'] } },
        ],
        sampleData: [
            {
                name: 'Production Servers',
                code: 'prod-servers',
                parentGroup: 'Data Center A',
                description: 'Production environment server group',
                enabled: 'true',
            },
            {
                name: 'Backup Servers',
                code: 'backup-servers',
                parentGroup: 'Data Center A',
                description: 'Backup server group',
                enabled: 'true',
            },
        ],
    },
    Clusters: {
        resourceType: 'Clusters',
        sheetName: 'Clusters',
        descriptionSheetName: '字段说明',
        fields: [
            { name: 'name', labelKey: 'field_clusters_name', enLabel: 'Cluster Name', zhLabel: '集群名称', required: true, validation: { type: 'string', maxLength: 100 } },
            { name: 'type', labelKey: 'field_clusters_type', enLabel: 'Cluster Type', zhLabel: '集群类型', required: true, validation: { type: 'string' } },
            { name: 'purpose', labelKey: 'field_clusters_purpose', enLabel: 'Purpose', zhLabel: '用途', required: false, validation: { type: 'string', maxLength: 500 } },
            { name: 'group', labelKey: 'field_clusters_group', enLabel: 'Host Group', zhLabel: '所属主机组', required: false, validation: { type: 'string' } },
            { name: 'description', labelKey: 'field_clusters_description', enLabel: 'Description', zhLabel: '描述', required: false, validation: { type: 'string', maxLength: 500 } },
        ],
        sampleData: [
            {
                name: 'Web Cluster 01',
                type: 'Kubernetes',
                purpose: 'Web application service',
                group: 'Production Servers',
                description: 'Production environment web cluster',
            },
            {
                name: 'Database Cluster 01',
                type: 'MySQL',
                purpose: 'Database service',
                group: 'Production Servers',
                description: 'Production environment database cluster',
            },
        ],
    },
    Hosts: {
        resourceType: 'Hosts',
        sheetName: 'Hosts',
        descriptionSheetName: '字段说明',
        fields: [
            { name: 'name', labelKey: 'field_hosts_name', enLabel: 'Host Name', zhLabel: '主机名称', required: true, validation: { type: 'string', maxLength: 100 } },
            { name: 'hostname', labelKey: 'field_hosts_hostname', enLabel: 'System Hostname', zhLabel: '系统主机名', required: false, validation: { type: 'string', maxLength: 255 } },
            { name: 'ip', labelKey: 'field_hosts_ip', enLabel: 'SSH IP Address', zhLabel: 'SSH IP 地址', required: true, validation: { type: 'ip' } },
            { name: 'port', labelKey: 'field_hosts_port', enLabel: 'Port', zhLabel: '端口', required: false, validation: { type: 'number' } },
            { name: 'businessIp', labelKey: 'field_hosts_businessIp', enLabel: 'Business IP Address', zhLabel: '业务 IP 地址', required: false, validation: { type: 'ip' } },
            { name: 'os', labelKey: 'field_hosts_os', enLabel: 'Operating System', zhLabel: '操作系统', required: false, validation: { type: 'string' } },
            { name: 'location', labelKey: 'field_hosts_location', enLabel: 'Location', zhLabel: '部署位置', required: false, validation: { type: 'string' } },
            { name: 'username', labelKey: 'field_hosts_username', enLabel: 'Username', zhLabel: '用户名', required: true, validation: { type: 'custom', customValidator: (value: string) => {
                if (!value) return { valid: false, error: 'Username is required' }
                if (!/^[\x00-\x7F]*$/.test(value)) {
                    return { valid: false, error: 'Username must contain only ASCII characters' }
                }
                return { valid: true }
            } } },
            { name: 'authType', labelKey: 'field_hosts_authType', enLabel: 'Auth Type', zhLabel: '认证类型（可选：password/key）', required: false, validation: { type: 'enum', enumValues: ['password', 'key'] } },
            { name: 'credential', labelKey: 'field_hosts_credential', enLabel: 'Credential', zhLabel: '凭证', required: false, validation: { type: 'custom', customValidator: (value: string) => {
                if (value && value !== '***' && !/^[\x00-\x7F]*$/.test(value)) {
                    return { valid: false, error: 'Credential must contain only ASCII characters' }
                }
                return { valid: true }
            } } },
            { name: 'business', labelKey: 'field_hosts_business', enLabel: 'Business', zhLabel: '业务', required: false, validation: { type: 'string' } },
            { name: 'cluster', labelKey: 'field_hosts_cluster', enLabel: 'Cluster', zhLabel: '所属集群', required: false, validation: { type: 'string' } },
            { name: 'purpose', labelKey: 'field_hosts_purpose', enLabel: 'Purpose', zhLabel: '用途', required: false, validation: { type: 'string' } },
            { name: 'role', labelKey: 'field_hosts_role', enLabel: 'Role', zhLabel: '角色（可选：primary/backup）', required: false, validation: { type: 'enum', enumValues: ['primary', 'backup'] } },
            { name: 'tags', labelKey: 'field_hosts_tags', enLabel: 'Tags', zhLabel: '标签', required: false, validation: { type: 'array', separator: ';' } },
            { name: 'description', labelKey: 'field_hosts_description', enLabel: 'Description', zhLabel: '描述', required: false, validation: { type: 'string', maxLength: 500 } },
        ],
        sampleData: [
            {
                name: 'Web Server 01',
                hostname: 'web-01.example.com',
                ip: '192.168.1.10',
                port: '22',
                businessIp: '10.0.1.10',
                os: 'CentOS 7.9',
                location: 'Data Center A - Rack 01',
                username: 'opsuser',
                authType: 'key',
                credential: '***',
                business: 'Web Application',
                cluster: 'Web Cluster 01',
                purpose: 'Web service',
                role: 'primary',
                tags: 'web;production',
                description: 'Production environment web server',
            },
            {
                name: 'DB Server 01',
                hostname: 'db-01.example.com',
                ip: '192.168.1.20',
                port: '22',
                businessIp: '10.0.1.20',
                os: 'Ubuntu 20.04',
                location: 'Data Center A - Rack 02',
                username: 'dbadmin',
                authType: 'password',
                credential: '***',
                business: 'Database',
                cluster: 'Database Cluster 01',
                purpose: 'Database service',
                role: 'primary',
                tags: 'database;production',
                description: 'Production environment database server',
            },
        ],
    },
    BusinessServices: {
        resourceType: 'BusinessServices',
        sheetName: 'Business Services',
        descriptionSheetName: '字段说明',
        fields: [
            { name: 'businessType', labelKey: 'field_businessServices_businessType', enLabel: 'Business Type', zhLabel: '业务类型', required: true, validation: { type: 'string' } },
            { name: 'name', labelKey: 'field_businessServices_name', enLabel: 'Business Name', zhLabel: '业务名称', required: true, validation: { type: 'string', maxLength: 100 } },
            { name: 'code', labelKey: 'field_businessServices_code', enLabel: 'Business Code', zhLabel: '业务编码', required: true, validation: { type: 'string', maxLength: 50 } },
            { name: 'priority', labelKey: 'field_businessServices_priority', enLabel: 'Priority', zhLabel: '优先级', required: false, validation: { type: 'string' } },
            { name: 'group', labelKey: 'field_businessServices_group', enLabel: 'Group', zhLabel: '所属分组', required: true, validation: { type: 'string' } },
            { name: 'tags', labelKey: 'field_businessServices_tags', enLabel: 'Tags', zhLabel: '标签', required: false, validation: { type: 'array', separator: ';' } },
            { name: 'description', labelKey: 'field_businessServices_description', enLabel: 'Description', zhLabel: '描述', required: false, validation: { type: 'string', maxLength: 500 } },
        ],
        sampleData: [
            {
                businessType: 'Web Application',
                name: 'Order Service',
                code: 'order-service',
                priority: 'P1',
                group: 'Production Servers',
                tags: 'core;payment',
                description: 'Order processing service',
            },
            {
                businessType: 'Web Application',
                name: 'User Service',
                code: 'user-service',
                priority: 'P1',
                group: 'Production Servers',
                tags: 'core;auth',
                description: 'User management service',
            },
        ],
    },
    Relations: {
        resourceType: 'Relations',
        sheetName: 'Relations',
        descriptionSheetName: '字段说明',
        fields: [
            { name: 'sourceNode', labelKey: 'field_relations_sourceNode', enLabel: 'Source Node', zhLabel: '源节点', required: true, validation: { type: 'string' } },
            { name: 'destNode', labelKey: 'field_relations_destNode', enLabel: 'Target Node', zhLabel: '目标节点', required: true, validation: { type: 'string' } },
            { name: 'description', labelKey: 'field_relations_description', enLabel: 'Description', zhLabel: '描述', required: false, validation: { type: 'string', maxLength: 500 } },
        ],
        sampleData: [
            {
                sourceNode: 'Web Server 01',
                destNode: 'DB Server 01',
                description: 'Web application accesses database',
            },
            {
                sourceNode: 'Web Server 01',
                destNode: 'Cache Server 01',
                description: 'Web application accesses cache service',
            },
        ],
    },
    SOPs: {
        resourceType: 'SOPs',
        sheetName: 'SOPs',
        descriptionSheetName: '字段说明',
        fields: [
            { name: 'name', labelKey: 'field_sops_name', enLabel: 'SOP Name', zhLabel: 'SOP 名称', required: true, validation: { type: 'string', maxLength: 100 } },
            { name: 'version', labelKey: 'field_sops_version', enLabel: 'Version', zhLabel: '版本号', required: false, validation: { type: 'string', maxLength: 50 } },
            { name: 'mode', labelKey: 'field_sops_mode', enLabel: 'Mode', zhLabel: '模式', required: false, validation: { type: 'enum', enumValues: ['structured', 'natural_language'] } },
            { name: 'enabled', labelKey: 'field_sops_enabled', enLabel: 'Enabled', zhLabel: '是否启用', required: false, validation: { type: 'enum', enumValues: ['true', 'false'] } },
            { name: 'description', labelKey: 'field_sops_description', enLabel: 'Description', zhLabel: '描述', required: false, validation: { type: 'string', maxLength: 500 } },
            { name: 'triggerCondition', labelKey: 'field_sops_triggerCondition', enLabel: 'Trigger Condition', zhLabel: '触发条件', required: false, validation: { type: 'string', maxLength: 500 } },
            { name: 'targetTags', labelKey: 'field_sops_targetTags', enLabel: 'Target Tags', zhLabel: '目标标签', required: false, validation: { type: 'array', separator: ';' } },
            { name: 'stepsDescription', labelKey: 'field_sops_stepsDescription', enLabel: 'Steps Description', zhLabel: '诊断步骤', required: false, validation: { type: 'string', maxLength: 1000 } },
            { name: 'nodes', labelKey: 'field_sops_nodes', enLabel: 'Nodes', zhLabel: '节点编辑器', required: false, validation: { type: 'string' } },
        ],
        sampleData: [
            {
                name: 'Server Restart',
                version: 'v1.0',
                mode: 'natural_language',
                enabled: 'true',
                description: 'Regularly restart servers to free resources',
                triggerCondition: 'Memory usage over 90%',
                targetTags: 'restart;ops;memory',
                stepsDescription: '1.Check current memory usage;2.Notify relevant personnel;3.Execute restart;4.Verify service recovery',
                nodes: '',
            },
            {
                name: 'Log Cleanup',
                version: 'v1.1',
                mode: 'natural_language',
                enabled: 'true',
                description: 'Regularly clean up log files',
                triggerCondition: 'Disk usage over 80%',
                targetTags: 'cleanup;ops;disk',
                stepsDescription: '1.Check log directory;2.Delete logs older than 7 days;3.Verify disk space',
                nodes: '',
            },
            {
                name: 'Web Service Recovery',
                version: 'v2.0',
                mode: 'structured',
                enabled: 'true',
                description: 'Structured workflow for web service recovery',
                triggerCondition: 'HTTP 5xx errors > 5%',
                targetTags: 'web;recovery;structured',
                stepsDescription: '',
                nodes: '[{"id":"node-1","name":"Start Diagnosis","type":"start","tags":[],"command":"","commandVariables":{},"variables":[],"outputFormat":"","analysisInstruction":"Start web service fault diagnosis","transitions":[{"condition":"default","description":"Start diagnosis","nextNodes":["node-2"]}]},{"id":"node-2","name":"Check Service Status","type":"analysis","tags":["check","status"],"command":"systemctl status nginx","commandVariables":{},"variables":[{"name":"service_status","defaultValue":"unknown","description":"Service running status","required":true}],"outputFormat":"json","analysisInstruction":"Check if nginx service is running and analyze status code","transitions":[{"condition":"service normal","description":"Service running normally","nextNodes":["node-4"]},{"condition":"service abnormal","description":"Service stopped or error","nextNodes":["node-3"]}]},{"id":"node-3","name":"Restart Service","type":"analysis","tags":["restart","operation"],"command":"systemctl restart nginx","commandVariables":{},"variables":[],"outputFormat":"text","analysisInstruction":"Execute nginx restart command and confirm success","transitions":[{"condition":"restart successful","description":"Service restarted","nextNodes":["node-4"]},{"condition":"restart failed","description":"Restart failed, manual intervention required","nextNodes":["node-5"]}]},{"id":"node-4","name":"Verify Service","type":"analysis","tags":["verify","test"],"command":"curl -I http://localhost","commandVariables":{},"variables":[],"outputFormat":"text","analysisInstruction":"Verify web service responds to HTTP requests","transitions":[{"condition":"verification passed","description":"Service recovered","nextNodes":["node-6"]},{"condition":"verification failed","description":"Service still has issues","nextNodes":["node-5"]}]},{"id":"node-5","name":"Manual Intervention","type":"browser","tags":["manual","notify"],"command":"","commandVariables":{},"variables":[],"outputFormat":"","analysisInstruction":"Notify operations team for manual handling","browserUrl":"https://ops.example.com/alert","browserAction":"open","browserMode":"headed","transitions":[]},{"id":"node-6","name":"End","type":"end","tags":[],"command":"","commandVariables":{},"variables":[],"outputFormat":"","analysisInstruction":"Diagnosis completed, service recovered","transitions":[]}]',
            },
        ],
    },
    Whitelist: {
        resourceType: 'Whitelist',
        sheetName: 'Whitelist',
        descriptionSheetName: '字段说明',
        fields: [
            { name: 'pattern', labelKey: 'field_whitelist_pattern', enLabel: 'Command', zhLabel: '命令', required: true, validation: { type: 'regex', pattern: '^[a-zA-Z0-9_\\-./\\s]+$' } },
            { name: 'description', labelKey: 'field_whitelist_description', enLabel: 'Description', zhLabel: '描述', required: false, validation: { type: 'string', maxLength: 500 } },
            { name: 'enabled', labelKey: 'field_whitelist_enabled', enLabel: 'Enabled', zhLabel: '是否启用', required: false, validation: { type: 'enum', enumValues: ['true', 'false'] } },
        ],
        sampleData: [
            { pattern: 'ls -la', description: 'List files', enabled: 'true' },
            { pattern: 'ps aux', description: 'View processes', enabled: 'true' },
            { pattern: 'cat /var/log/syslog', description: 'View logs', enabled: 'false' },
        ],
    },
}

export function getValidationRuleDescription(validation: FieldMetadata['validation'], t: (key: string, params?: Record<string, any>) => string): string {
    if (!validation) return ''
    const { type, maxLength, minLength, enumValues, pattern, separator } = validation
    const rules: string[] = []

    if (type === 'string') {
        rules.push(t('importExport.validationString'))
        if (maxLength) rules.push(t('importExport.validationMaxLength', { max: maxLength }))
        if (minLength) rules.push(t('importExport.validationMinLength', { min: minLength }))
    } else if (type === 'number') {
        rules.push(t('importExport.validationNumber'))
    } else if (type === 'boolean') {
        rules.push(t('importExport.validationBoolean'))
    } else if (type === 'enum') {
        rules.push(`${t('importExport.validationEnum')}: ${enumValues?.join('/')}`)
    } else if (type === 'ip') {
        rules.push(t('importExport.validationIp'))
    } else if (type === 'regex') {
        rules.push(`${t('importExport.validationRegex')}: ${pattern}`)
    } else if (type === 'array') {
        rules.push(t('importExport.validationArray').replace('分隔符', separator || ';'))
    } else if (type === 'custom') {
        rules.push(t('importExport.validationCustom'))
    }

    return rules.join('，')
}

export function getRequiredLabel(required: boolean, t: (key: string) => string): string {
    return required ? t('importExport.requiredLabel') : t('importExport.optionalLabel')
}

export function getExcelColumn(index: number): string {
    let column = ''
    while (index >= 0) {
        column = String.fromCharCode(65 + (index % 26)) + column
        index = Math.floor(index / 26) - 1
    }
    return column
}
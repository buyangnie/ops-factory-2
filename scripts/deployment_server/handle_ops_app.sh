#!/bin/bash

# Gateway JAR文件复制脚本 - 增强版
# 功能：复制JAR文件并设置权限，同步lib/agents，合并config.yaml，然后重启gateway服务

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置文件路径
CONFIG_FILE="./handle_ops_app.conf"
ADVANCED_CONFIG_FILE="./handle_ops_app_advanced.conf"
timestamp=$(date "+%Y%m%d%H%M")

# 加载配置文件的函数
load_config() {
    local config_file=$1

    if [ -f "$config_file" ]; then
        echo -e "${BLUE}正在加载配置文件: $config_file${NC}"
        # 使用source加载配置文件，支持变量和命令替换
        source "$config_file"

        # 显示加载的配置信息
        echo -e "${GREEN}配置加载完成:${NC}"
        echo -e "  源目录: ${SOURCE_DIR}"
        echo -e "  目标目录: ${TARGET_DIR}"
        echo -e "  库目录: ${TARGET_LIB_DIR}"
        echo -e "  Agents目录: ${TARGET_AGENTS_DIR}"
        echo -e "  Gateway JAR: ${GATEWAY_JAR}"
        echo -e "  LIB ZIP: ${LIB_ZIP}"
        echo -e "  Agents ZIP: ${AGENTS_ZIP}"
        echo -e "  OI JAR: ${OI_JAR}"
        echo -e "  CC JAR: ${CC_JAR}"
        echo -e "  KS JAR: ${KS_JAR}"
        echo -e "  ROOT_PASSWORD: ${ROOT_PASSWORD:0:8}****"
        echo -e "  日志级别: ${LOG_LEVEL}"
        echo ""
    else
        echo -e "${RED}错误：配置文件 $config_file 不存在！${NC}"
        exit 1
    fi
}

# 日志记录函数
log() {
    local level=$1
    local message=$2

    if [ "$LOG_LEVEL" = "verbose" ] || [ "$LOG_LEVEL" = "debug" ]; then
        echo -e "$(date '+%Y-%m-%d %H:%M:%S') [$level] $message"
    fi

    if [ "$LOG_LEVEL" = "debug" ]; then
        # debug模式下保存到日志文件
        echo "$(date '+%Y-%m-%d %H:%M:%S') [$level] $message" >> /tmp/gateway_copy.log
    fi
}

# 颜色打印函数
print_info() {
    echo -e "${BLUE}[信息]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[成功]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[警告]${NC} $1"
}

print_error() {
    echo -e "${RED}[错误]${NC} $1"
}

# 函数：检查命令是否存在
check_command() {
    if ! command -v $1 &> /dev/null; then
        print_error "命令 $1 未找到，请安装："
        echo "  Ubuntu/Debian: sudo apt-get install $1"
        echo "  CentOS/RHEL: sudo yum install $1"
        exit 1
    fi
}

# 函数：验证源文件
validate_source_files() {
    print_info "验证源文件..."

    # 检查源目录是否存在
    if [ ! -d "$SOURCE_DIR" ]; then
        print_error "源目录不存在: $SOURCE_DIR"
        exit 1
    fi

    # 检查源文件是否存在
    if [ ! -f "${SOURCE_DIR}${GATEWAY_JAR}" ]; then
        print_error "源文件不存在: ${SOURCE_DIR}${GATEWAY_JAR}"
        exit 1
    fi

    if [ ! -f "${SOURCE_DIR}${LIB_ZIP}" ]; then
        print_warning "lib压缩包不存在: ${SOURCE_DIR}${LIB_ZIP}，将跳过lib更新"
        LIB_ZIP=""
    fi

    if [ ! -f "${SOURCE_DIR}${AGENTS_ZIP}" ]; then
        print_warning "agents压缩包不存在: ${SOURCE_DIR}${AGENTS_ZIP}，将跳过agents同步"
        AGENTS_ZIP=""
    fi

    if [ ! -f "${SOURCE_DIR}${OI_JAR}" ]; then
        print_warning "OI JAR不存在: ${SOURCE_DIR}${OI_JAR}，将跳过OI部署"
        OI_JAR=""
    fi

    if [ ! -f "${SOURCE_DIR}${DV_SERVER}" ]; then
        print_warning "dv_server.py不存在: ${SOURCE_DIR}${DV_SERVER}，将跳过DV mock部署"
        DV_SERVER=""
    fi

    if [ ! -f "${SOURCE_DIR}${CC_JAR}" ]; then
        print_warning "Control Center JAR不存在: ${SOURCE_DIR}${CC_JAR}，将跳过CC部署"
        CC_JAR=""
    fi

    if [ ! -f "${SOURCE_DIR}${KS_JAR}" ]; then
        print_warning "Knowledge Service JAR不存在: ${SOURCE_DIR}${KS_JAR}，将跳过KS部署"
        KS_JAR=""
    fi

    print_success "源文件验证通过"
}

# 函数：显示操作计划
show_operations() {
    echo ""
    echo "=========================================="
    echo "    Gateway JAR文件复制操作计划"
    echo "=========================================="
    echo ""
    echo "将要执行的操作："
    echo "  1. 检查并创建目标目录"
    echo "  2. 备份当前部署"
    echo "  3. 复制 gateway-service.jar"
    echo "  4. 更新 lib 依赖目录"
    echo "  5. 同步 agents 目录（排除 config.yaml、secrets.yaml、goose/）"
    echo "  6. 部署 operation-intelligence（复制 JAR、合并配置）"
    echo "  7. 部署 dv_server.py mock 服务"
    echo "  8. 部署 control-center（复制 JAR、合并配置）"
    echo "  9. 部署 knowledge-service（复制 JAR、合并配置）"
    echo "  10. 合并 gateway config.yaml（保留环境特有的 server 设置）"
    echo "  11. 复制 webapp"
    echo "  12. 设置文件权限"
    echo "  13. 重启所有服务（gateway + OI + dv_server + CC + KS + webapp）"
    echo ""
}

# 主函数
main() {
    echo ""
    echo "=========================================="
    echo "  Gateway JAR文件复制脚本"
    echo "=========================================="
    echo ""

    # 检查配置文件
    if [ -f "$ADVANCED_CONFIG_FILE" ]; then
        load_config "$ADVANCED_CONFIG_FILE"
    elif [ -f "$CONFIG_FILE" ]; then
        load_config "$CONFIG_FILE"
    else
        print_error "未找到配置文件！"
        echo "请确保以下文件之一存在："
        echo "  - $CONFIG_FILE"
        echo "  - $ADVANCED_CONFIG_FILE"
        exit 1
    fi

    # 检查必需的命令
    check_command expect

    # 验证源文件
    validate_source_files

    # 显示操作计划
    show_operations
    print_info "开始执行..."

    # 创建部署脚本（所有操作写入脚本文件，避免 expect send 逐行发送的时序问题）
    deploy_script=$(mktemp /tmp/deploy_gateway_XXXXXX.sh)
    cat << DEPLOY_EOF > "$deploy_script"
#!/bin/bash

echo "=== 开始部署 ==="

# 1. 创建目标目录
echo "正在创建目标目录..."
mkdir -p "${TARGET_DIR}"
mkdir -p "${TARGET_LIB_DIR}"
mkdir -p "${TARGET_AGENTS_DIR}"
mkdir -p "${TARGET_WEBAPP_DIR}"
mkdir -p "${BACKUP_DIR}"

# 2. 备份文件
echo "创建备份到: ${BACKUP_DIR}"
cd "${TARGET_HOME_DIR}"
# 只备份必要的文件，排除 logs、data 目录和 .bak 文件
tar zcvf "backup_${timestamp}.tar.gz" \
    --exclude='*/logs' \
    --exclude='*/data' \
    --exclude='*/config.yaml.bak*' \
    --exclude='*/.bak' \
    webapp \
    gateway/gateway/gateway-service.jar \
    gateway/gateway/lib/ \
    gateway/gateway/agents/ \
    gateway/gateway/config.yaml \
    operation-intelligence/operation-intelligence.jar \
    operation-intelligence/config.yaml \
    control-center/config.yaml \
    control-center/control-center.jar \
    knowledge-service/config.yaml \
    knowledge-service/knowledge-service.jar
mv "backup_${timestamp}.tar.gz" "${BACKUP_DIR}"

echo "保留最近5个备份文件..."
cd "${BACKUP_DIR}" && ls -t *.tar.gz 2>/dev/null | tail -n +6 | xargs -r rm -f

# 清理旧的 config.yaml.bak 文件（保留最近的5个）
echo "清理旧的配置备份文件..."
find "${TARGET_DIR}" -maxdepth 1 -name "config.yaml.bak*" -type f | xargs ls -t 2>/dev/null | tail -n +6 | xargs -r rm -f
find "${TARGET_OI_DIR}" -maxdepth 1 -name "config.yaml.bak*" -type f 2>/dev/null | xargs ls -t | tail -n +6 | xargs -r rm -f
find "${TARGET_CC_DIR}" -maxdepth 1 -name "config.yaml.bak*" -type f 2>/dev/null | xargs ls -t | tail -n +6 | xargs -r rm -f
find "${TARGET_KS_DIR}" -maxdepth 1 -name "config.yaml.bak*" -type f 2>/dev/null | xargs ls -t | tail -n +6 | xargs -r rm -f

# 3. 复制 gateway-service.jar
echo "正在复制 ${GATEWAY_JAR}..."
echo "yes" | cp "${SOURCE_DIR}${GATEWAY_JAR}" "${TARGET_DIR}${GATEWAY_JAR}"

# 4. 更新 lib 依赖目录
if [ -n "${LIB_ZIP}" ] && [ -f "${SOURCE_DIR}${LIB_ZIP}" ]; then
    echo "正在更新 lib 目录..."
    rm -f "${TARGET_LIB_DIR}"*.jar
    echo "A" | unzip -o "${SOURCE_DIR}${LIB_ZIP}" -d "${TARGET_LIB_DIR}"
fi

# 5. 同步 agents 目录（排除 config.yaml、secrets.yaml、goose/ 目录）
if [ -n "${AGENTS_ZIP}" ] && [ -f "${SOURCE_DIR}${AGENTS_ZIP}" ]; then
    echo "正在同步 agents 目录..."
    mkdir -p /tmp/agents-update
    echo "A" | unzip -o "${SOURCE_DIR}${AGENTS_ZIP}" -d /tmp/agents-update
    rsync -av --delete --exclude='config.yaml' --exclude='secrets.yaml' --exclude='goose/' /tmp/agents-update/ "${TARGET_AGENTS_DIR}" || true
    rm -rf /tmp/agents-update
fi

# 6. 部署 operation-intelligence
if [ -n "${OI_JAR}" ] && [ -f "${SOURCE_DIR}${OI_JAR}" ]; then
    echo "正在部署 operation-intelligence..."
    mkdir -p "${TARGET_OI_DIR}"

    # 备份旧配置
    if [ -f "${TARGET_OI_DIR}config.yaml" ]; then
        cp "${TARGET_OI_DIR}config.yaml" "${TARGET_OI_DIR}config.yaml.bak.${timestamp}"
    fi

    # 复制新 JAR
    echo "yes" | cp "${SOURCE_DIR}${OI_JAR}" "${TARGET_OI_DIR}${OI_JAR}"

    # 复制并合并配置（保留环境特有的 dv-environments）
    if [ -f "${SOURCE_DIR}${OI_CONFIG}" ]; then
        cp "${SOURCE_DIR}${OI_CONFIG}" "${TARGET_OI_DIR}config.yaml"
        if [ -f "${TARGET_OI_DIR}config.yaml.bak.${timestamp}" ]; then
            python3 -c "
import yaml
bak_path = '${TARGET_OI_DIR}config.yaml.bak.${timestamp}'
cur_path = '${TARGET_OI_DIR}config.yaml'
with open(bak_path) as f:
    old = yaml.safe_load(f) or {}
with open(cur_path) as f:
    cur = yaml.safe_load(f) or {}
# 保留环境特有的 dv-environments
old_dv = old.get('operation-intelligence', {}).get('qos', {}).get('dv-environments')
if old_dv is not None:
    cur_oi = cur.setdefault('operation-intelligence', {})
    cur_qos = cur_oi.setdefault('qos', {})
    cur_qos['dv-environments'] = old_dv
with open(cur_path, 'w') as f:
    yaml.dump(cur, f, default_flow_style=False, allow_unicode=True, sort_keys=False)
print('OI config.yaml 合并完成')
"
            rm -f "${TARGET_OI_DIR}config.yaml.bak.${timestamp}"
        fi
    fi

    chown root:root "${TARGET_OI_DIR}${OI_JAR}"
    chmod 600 "${TARGET_OI_DIR}${OI_JAR}"
fi

# 7. 部署 dv_server.py mock 服务
if [ -n "${DV_SERVER}" ] && [ -f "${SOURCE_DIR}${DV_SERVER}" ]; then
    echo "正在部署 dv_server.py..."
    mkdir -p "${DV_MOCK_DIR}"
    cp "${SOURCE_DIR}${DV_SERVER}" "${DV_MOCK_DIR}dv_server.py"
    chmod +x "${DV_MOCK_DIR}dv_server.py"
fi

# 8. 部署 control-center
if [ -n "${CC_JAR}" ] && [ -f "${SOURCE_DIR}${CC_JAR}" ]; then
    echo "正在部署 control-center..."
    mkdir -p "${TARGET_CC_DIR}"

    # 备份旧配置
    if [ -f "${TARGET_CC_DIR}config.yaml" ]; then
        cp "${TARGET_CC_DIR}config.yaml" "${TARGET_CC_DIR}config.yaml.bak.${timestamp}"
    fi

    # 复制新 JAR
    echo "yes" | cp "${SOURCE_DIR}${CC_JAR}" "${TARGET_CC_DIR}${CC_JAR}"

    # 直接替换配置
    if [ -f "${SOURCE_DIR}${CC_CONFIG}" ]; then
        echo "yes" | cp "${SOURCE_DIR}${CC_CONFIG}" "${TARGET_CC_DIR}config.yaml"
        # 修改 cors-origin 为 *
        sed -i 's/^  cors-origin:.*/  cors-origin: "*"/' "${TARGET_CC_DIR}config.yaml"
    fi

    chown root:root "${TARGET_CC_DIR}${CC_JAR}"
    chmod 600 "${TARGET_CC_DIR}${CC_JAR}"
fi

# 9. 部署 knowledge-service
if [ -n "${KS_JAR}" ] && [ -f "${SOURCE_DIR}${KS_JAR}" ]; then
    echo "正在部署 knowledge-service..."
    mkdir -p "${TARGET_KS_DIR}"

    # 备份旧配置
    if [ -f "${TARGET_KS_DIR}config.yaml" ]; then
        cp "${TARGET_KS_DIR}config.yaml" "${TARGET_KS_DIR}config.yaml.bak.${timestamp}"
    fi

    # 复制新 JAR
    echo "yes" | cp "${SOURCE_DIR}${KS_JAR}" "${TARGET_KS_DIR}${KS_JAR}"

    # 直接替换配置
    if [ -f "${SOURCE_DIR}${KS_CONFIG}" ]; then
        echo "yes" | cp "${SOURCE_DIR}${KS_CONFIG}" "${TARGET_KS_DIR}config.yaml"
    fi

    chown root:root "${TARGET_KS_DIR}${KS_JAR}"
    chmod 600 "${TARGET_KS_DIR}${KS_JAR}"
fi

# 10. 合并 config.yaml
#    策略：以代码仓新 config 为基准，仅保留环境特有的 server 设置
echo "正在合并 config.yaml..."
cp "${TARGET_DIR}config.yaml" "${TARGET_DIR}config.yaml.bak.${timestamp}"
cp "${SOURCE_DIR}config.yaml" "${TARGET_DIR}config.yaml"

python3 -c "
import yaml

bak_path  = '${TARGET_DIR}config.yaml.bak.${timestamp}'
cur_path  = '${TARGET_DIR}config.yaml'

with open(bak_path) as f:
    old = yaml.safe_load(f) or {}
with open(cur_path) as f:
    cur = yaml.safe_load(f) or {}

# 仅保留环境特有的 server 设置（端口、地址、SSL 等）
if 'server' in old:
    cur['server'] = old['server']

with open(cur_path, 'w') as f:
    yaml.dump(cur, f, default_flow_style=False, allow_unicode=True, sort_keys=False)

print('config.yaml 合并完成')
"
rm -f "${TARGET_DIR}config.yaml.bak.${timestamp}"

# 11. 复制 webapp
if [ -n "${WEBAPP_ZIP}" ] && [ -f "${SOURCE_DIR}${WEBAPP_ZIP}" ]; then
    echo "正在复制 webapp..."
    rm -rf "${TARGET_WEBAPP_DIR}"/assets/*.js "${TARGET_WEBAPP_DIR}"/assets/*.css
    cd "${TARGET_WEBAPP_DIR}"
    echo "A" | unzip "${SOURCE_DIR}${WEBAPP_ZIP}"
fi

# 12. 设置文件权限
echo "设置文件权限..."
chown root:root "${TARGET_DIR}${GATEWAY_JAR}"
chmod 600 "${TARGET_DIR}${GATEWAY_JAR}"

# 13. 重启服务
echo "重启服务..."

echo "正在停止gateway进程..."
eval "${KILL_GATAWAY_COMMAND}" || true
echo "正在停止OI进程..."
eval "${KILL_OI_COMMAND}" || true
echo "正在停止DV mock进程..."
eval "${KILL_DV_MOCK_COMMAND}" || true
echo "正在停止CC进程..."
eval "${KILL_CC_COMMAND}" || true
echo "正在停止KS进程..."
eval "${KILL_KS_COMMAND}" || true
echo "正在停止WEBAPP进程..."
eval "${KILL_WEBAPP_COMMAND}" || true
pkill -9 goosed 2>/dev/null || true
sleep 2

# 启动gateway服务
echo "正在启动gateway服务..."
cd "${TARGET_DIR}"
export GATEWAY_API_PASSWORD=ms@123
nohup java -Dloader.path=lib -jar ${GATEWAY_JAR} --spring.config.location=config.yaml --server.port=3000 --gateway.cors-origin='*' > gateway.log 2>&1 &
sleep 5

# 启动 dv_server.py mock 服务
if [ -f "${DV_MOCK_DIR}dv_server.py" ]; then
    echo "正在启动 dv_server.py mock 服务..."
    cd "${DV_MOCK_DIR}"
    nohup python3 dv_server.py > dv_server.log 2>&1 &
    sleep 3
fi

# 启动 OI 服务
if [ -f "${TARGET_OI_DIR}${OI_JAR}" ]; then
    echo "正在启动 operation-intelligence 服务..."
    cd "${TARGET_OI_DIR}"
    nohup java -jar ${OI_JAR} --spring.config.location=config.yaml > oi.log 2>&1 &
    sleep 5
fi

# 启动 control-center 服务
if [ -f "${TARGET_CC_DIR}${CC_JAR}" ]; then
    echo "正在启动 control-center 服务..."
    cd "${TARGET_CC_DIR}"
    nohup java -jar ${CC_JAR} --spring.config.location=config.yaml > cc.log 2>&1 &
    sleep 5
fi

# 启动 knowledge-service 服务
if [ -f "${TARGET_KS_DIR}${KS_JAR}" ]; then
    echo "正在启动 knowledge-service 服务..."
    cd "${TARGET_KS_DIR}"
    nohup java -jar ${KS_JAR} --spring.config.location=config.yaml > ks.log 2>&1 &
    sleep 5
fi

# 启动webapp服务
echo "正在启动webapp服务..."
cd "${TARGET_WEBAPP_DIR}"
nohup python -m http.server 5173 > webapp.log 2>&1 &
sleep 5

echo "=== 部署完成 ==="
DEPLOY_EOF

    chmod +x "$deploy_script"

    # 创建expect脚本（仅负责 su 切换 root 并执行部署脚本）
    expect_script=$(mktemp)
    cat << EOF > "$expect_script"
#!/usr/bin/expect -f

set timeout 120

# 启动su命令切换到root
spawn su - root

# 等待密码提示并输入密码
expect {
    "Password:" {
        send "${ROOT_PASSWORD}\r"
    }
    timeout {
        puts "错误：等待密码提示超时"
        exit 1
    }
}

# 等待 root 提示符后执行部署脚本
expect {
    "# " {
        send "bash ${deploy_script}\r"
    }
    timeout {
        puts "错误：获取root权限超时"
        exit 1
    }
}

# 等待部署脚本执行完成
expect {
    "=== 部署完成 ===" {
        puts "\n部署脚本执行成功"
    }
    timeout {
        puts "错误：部署脚本执行超时"
        exit 1
    }
}

# 等待 root 提示符后清理并退出
expect {
    "# " {
        send "rm -f ${deploy_script}\r"
    }
    timeout {
        puts "警告：等待退出超时"
    }
}

expect {
    "# " {
        send "exit\r"
    }
    timeout {}
}

expect eof
EOF

    chmod +x "$expect_script"

    # 执行expect脚本
    echo ""
    print_info "正在执行权限切换和文件操作..."
    if "$expect_script"; then
        echo ""
        print_success "文件复制和权限设置完成！"

        # 检查服务状态
        print_info "检查Gateway服务状态..."
        if pgrep -f "gateway-service.jar" > /dev/null; then
            print_success "Gateway服务正在运行"
            echo "进程信息："
            ps -ef | grep "gateway-service.jar" | grep -v grep
        else
            print_warning "Gateway服务未运行，请检查日志"
        fi

        print_info "检查webapp服务状态..."
        if pgrep -f "http.server" > /dev/null; then
            print_success "Webapp服务正在运行"
            echo "进程信息："
            ps -ef | grep "http.server" | grep -v grep
        else
            print_warning "Webapp服务未运行，请检查日志"
        fi

        print_info "检查control-center服务状态..."
        if pgrep -f "control-center.jar" > /dev/null; then
            print_success "Control Center服务正在运行"
            echo "进程信息："
            ps -ef | grep "control-center.jar" | grep -v grep
        else
            print_warning "Control Center服务未运行，请检查日志"
        fi

        print_info "检查knowledge-service服务状态..."
        if pgrep -f "knowledge-service.jar" > /dev/null; then
            print_success "Knowledge Service服务正在运行"
            echo "进程信息："
            ps -ef | grep "knowledge-service.jar" | grep -v grep
        else
            print_warning "Knowledge Service服务未运行，请检查日志"
        fi
    else
        echo ""
        print_error "操作失败！"
        exit 1
    fi

    # 清理临时文件
    rm -f "$expect_script"

    echo ""
    print_success "脚本执行完成"
}

# 处理命令行参数
CONFIG_FILE_ARG=""
BACKUP_MODE_ARG=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --config)
            CONFIG_FILE_ARG="$2"
            shift 2
            ;;
        --verbose)
            LOG_LEVEL="verbose"
            shift
            ;;
        --debug)
            LOG_LEVEL="debug"
            shift
            ;;
        *)
            print_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
done

# 应用命令行参数
if [ -n "$CONFIG_FILE_ARG" ]; then
    CONFIG_FILE="$CONFIG_FILE_ARG"
fi

# 执行主函数
main

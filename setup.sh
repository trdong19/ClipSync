#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== ClipSync 一键安装启动脚本 ===${NC}"

# 1. 检测系统类型
if [ -f /etc/debian_version ]; then
    PKG_MANAGER="apt"
elif [ -f /etc/redhat-release ]; then
    PKG_MANAGER="yum"
elif [ -f /etc/arch-release ]; then
    PKG_MANAGER="pacman"
else
    PKG_MANAGER="unknown"
fi

# 2. 安装系统依赖
install_system_deps() {
    echo -e "${YELLOW}[*] 检查系统依赖...${NC}"

    # 检查 python3
    if ! command -v python3 &> /dev/null; then
        echo "[!] 未找到 python3，正在安装..."
        case $PKG_MANAGER in
            apt) sudo apt update && sudo apt install -y python3 ;;
            yum) sudo yum install -y python3 ;;
            pacman) sudo pacman -S --noconfirm python ;;
        esac
    fi

    # 获取 python 版本
    PYTHON_VERSION=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
    echo "[*] Python 版本: $PYTHON_VERSION"

    # 检查 venv 模块
    if ! python3 -m venv --help &> /dev/null; then
        echo "[!] python3-venv 未安装，正在安装..."
        case $PKG_MANAGER in
            apt) sudo apt install -y "python${PYTHON_VERSION}-venv" python3-pip ;;
            yum) sudo yum install -y python3-pip ;;
            pacman) sudo pacman -S --noconfirm python-pip ;;
        esac
    fi

    echo -e "${GREEN}[✓] 系统依赖检查完成${NC}"
}

# 3. 创建虚拟环境
setup_venv() {
    echo -e "${YELLOW}[*] 设置虚拟环境...${NC}"

    if [ -d "venv" ]; then
        echo "[*] 虚拟环境已存在，跳过创建"
    else
        python3 -m venv venv
        echo "[*] 虚拟环境创建成功"
    fi

    # 激活虚拟环境
    source venv/bin/activate

    # 升级 pip
    pip install --upgrade pip -q

    echo -e "${GREEN}[✓] 虚拟环境设置完成${NC}"
}

# 4. 安装 Python 依赖
install_python_deps() {
    echo -e "${YELLOW}[*] 安装 Python 依赖...${NC}"

    if [ -f "requirements.txt" ]; then
        pip install -r requirements.txt -q
        echo -e "${GREEN}[✓] 依赖安装完成${NC}"
    else
        echo "[!] 未找到 requirements.txt"
        exit 1
    fi
}

# 5. 创建桌面快捷方式
create_desktop_shortcut() {
    echo -e "${YELLOW}[*] 创建桌面快捷方式...${NC}"

    DESKTOP_FILE="$HOME/.local/share/applications/clipsync.desktop"
    ICON_PATH="$SCRIPT_DIR/static/img/logo.png"

    mkdir -p "$HOME/.local/share/applications"

    cat > "$DESKTOP_FILE" << EOF
[Desktop Entry]
Name=ClipSync
Comment=跨设备剪贴板同步
Exec=bash -c "cd $SCRIPT_DIR && source venv/bin/activate && python3 main.py"
Icon=${ICON_PATH:-utilities-terminal}
Terminal=true
Type=Application
Categories=Utility;
EOF

    echo -e "${GREEN}[✓] 桌面快捷方式创建完成${NC}"
}

# 6. 启动应用
start_app() {
    echo -e "${GREEN}[*] 启动 ClipSync...${NC}"
    echo ""
    python3 main.py
}

# 执行流程
install_system_deps
setup_venv
install_python_deps
create_desktop_shortcut
start_app

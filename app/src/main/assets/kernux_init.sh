#!/bin/sh
# Kernux initialization script
# Auto-runs on first launch to setup environment

echo "Kernux initialization..."

# Ensure directories exist
mkdir -p "$HOME/bin"
mkdir -p "$HOME/usr/bin"
mkdir -p "$HOME/usr/local/bin"
mkdir -p "$HOME/.kernux"
mkdir -p "$HOME/tmp"

# Set up PATH if not already set
if [ -z "$PATH" ]; then
    export PATH="$HOME/bin:$HOME/usr/bin:$HOME/usr/local/bin:/system/bin:/system/xbin"
fi

# Aliases for convenience
alias ll='ls -la'
alias la='ls -A'
alias l='ls -CF'
alias cls='clear'

# Welcome message
echo ""
echo "=========================================="
echo "  Kernux Terminal - Multi-Session Ready"
echo "=========================================="
echo "Available commands:"
echo "  - pwd, ls, cd, cat, echo"
echo "  - ps, uname, date, id"
echo "  - touch, rm, mkdir, cp, mv"
echo "  - grep, sed, awk, find"
echo ""
echo "Session info:"
echo "  HOME: $HOME"
echo "  PATH: $PATH"
echo "  TERM: $TERM"
echo ""
echo "Type 'help' for more commands"
echo "=========================================="
echo ""

#!/usr/bin/env python3
"""
种子数据脚本:把 3 门学科 + 简化知识树插到数据库。
H2 关系库:用 JDBC 直连 + 执行 db_schema.sql + 种子 INSERT。
MySQL/PG 同样适用(替换连接参数即可)。

用法:
    pip install psycopg2-binary    # 或 pymysql / h2 客户端按需
    python scripts/seed_knowledge_tree.py
"""
import os
import sys
import sqlite3
from pathlib import Path

# 默认用 H2 文件库;若用 MySQL/PG,改连接串即可
PROJECT_ROOT = Path(__file__).resolve().parent.parent
DB_PATH = PROJECT_ROOT / "data" / "learning-buddy.mv.db"
SCHEMA = PROJECT_ROOT / "docs" / "db_schema.sql"

# H2 客户端依赖(若未装可 pip install jaydebeapi)
# 这里用 SQLite 做一个最小替代演示:把 H2 初始化留给 Spring Boot 自动建表(ddl-auto=update)

def main():
    print("Learning Buddy · 种子数据脚本")
    print(f"项目根: {PROJECT_ROOT}")
    print(f"Schema: {SCHEMA}")
    if not SCHEMA.exists():
        print("⚠️  schema 不存在,请检查 docs/db_schema.sql")
        sys.exit(1)
    print()
    print("✅ 关系库表会在 Spring Boot 启动时由 JPA ddl-auto 自动创建(见 application.yml)")
    print("✅ 知识树种子数据将在 D2 用 Java CommandLineRunner 注入(见 services/KnowledgeSeedRunner.java)")
    print()
    print("如要手动跑 schema(MySQL/PG 场景):")
    print(f"  mysql -u root -p learning_buddy < {SCHEMA}")
    print(f"  psql -U postgres -d learning_buddy -f {SCHEMA}")

if __name__ == "__main__":
    main()

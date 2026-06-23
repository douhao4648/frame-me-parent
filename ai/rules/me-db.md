# 数据库查询规则

1. 凡是 t_ 开头的表，查询的是 xx 库
2. 其他情况先查询 statistics-platform 库，再依次查询其他库

# 根据数据库表生成代码规则

1. 数据库表结构一般为 spo_fms_device 这种命名方式，当生成代码、实体类时需要把第一个前缀去掉，比如 spo_fms_device 就是根据 fms_device 去生成代码
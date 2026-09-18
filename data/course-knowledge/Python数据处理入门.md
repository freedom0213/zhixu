# Python 数据处理入门

> 本文件为「知序学堂」课程知识库资料，供课程 AI 助教检索与问答使用。

## 课程简介

用 NumPy 与 Pandas 完成从原始数据到分析结论的完整流程。

本课程先建立数据处理流程的整体认知，讲解 NumPy 数组与 Pandas 的 Series、DataFrame；随后覆盖缺失值、重复值、类型转换与分组聚合等清洗与转换操作，最后落到描述性统计、表连接与可视化图表。

---

## 第一章 数据处理环境

本章搭好工具链，理解各库的分工。

### 1.1 数据处理流程概览

**学习目标**：理解从采集到输出的标准环节。

**核心知识点**

- 标准流程：采集 → 清洗 → 转换 → 分析 → 可视化 → 输出
- 实际工作中清洗与转换往往占据大部分时间
- 每一步都应可复现，避免只在交互式环境里手工改数据
- 中间结果建议落盘保存，便于回溯与增量处理

**示例**

read_csv → dropna → astype → groupby → describe → plot → to_excel

**易错点**：直接在原始数据上手工编辑，一旦流程需要重跑就无法复现。

### 1.2 NumPy 数组基础

**学习目标**：掌握 ndarray 的创建、索引与向量化运算。

**核心知识点**

- ndarray 是同类型元素的多维数组，支持向量化运算，性能远优于 Python 循环
- 常用创建方式：array、zeros、ones、arange、linspace
- 索引支持切片、布尔索引与花式索引
- reshape 改变形状不复制数据，转置用 T 或 transpose

**示例**

```python
import numpy as np
a = np.array([[1, 2, 3], [4, 5, 6]])
print(a[a > 3])        # 布尔索引
print(a.sum(axis=0))   # 按列求和
```

**易错点**：用 Python for 循环遍历 NumPy 数组做逐元素运算，完全浪费了向量化带来的性能优势。

### 1.3 Pandas 数据结构

**学习目标**：掌握 Series 与 DataFrame 的基本操作。

**核心知识点**

- Series 是带索引的一维数组，DataFrame 是共享行索引的二维表
- 常用属性：shape、dtypes、columns、index；常用方法：head、info、describe
- loc 按标签定位，iloc 按位置定位，两者不可混用
- 数据读写：read_csv、read_excel、to_csv、to_excel

**示例**

```python
import pandas as pd
df = pd.read_csv('orders.csv')
print(df.head())
print(df.loc[0, 'amount'])   # 按标签
print(df.iloc[0, 2])         # 按位置
```

**易错点**：把 loc 与 iloc 混用，在索引非默认整数序列时会取到错误的数据行。

---

## 第二章 数据清洗与转换

本章解决真实数据中的脏乱问题。

### 2.1 缺失值与重复值

**学习目标**：掌握缺失与重复的处理策略。

**核心知识点**

- isnull().sum() 快速统计各列缺失情况，是清洗第一步
- 缺失处理：删除、填充固定值、填充均值中位数、前向或后向填充
- drop_duplicates 可指定列子集去重，keep 参数决定保留哪一条
- 填充策略要结合业务含义，数值列与分类列的合理填充方式不同

**示例**

```python
print(df.isnull().sum())
df['age'] = df['age'].fillna(df['age'].median())
df = df.drop_duplicates(subset=['order_no'])
```

**易错点**：用均值填充有偏分布的数据，会改变数据的真实分布形态，影响后续统计结论。

### 2.2 类型转换与字段派生

**学习目标**：会修正数据类型并派生分析所需字段。

**核心知识点**

- astype 用于类型转换，to_datetime 用于日期解析并支持错误处理参数
- 字符串列常用 str.strip、str.replace、str.contains 做清洗
- apply 可对行或列应用自定义函数，lambda 适合简单逻辑
- 时间字段应拆出年、月、日、星期等维度，便于后续分组分析

**示例**

```python
df['created_at'] = pd.to_datetime(df['created_at'], errors='coerce')
df['month'] = df['created_at'].dt.to_period('M')
df['name'] = df['name'].str.strip()
```

**易错点**：转换失败时不加 errors='coerce'，遇到脏数据会直接抛异常中断整个流程。

### 2.3 分组聚合与透视

**学习目标**：掌握 groupby 与 pivot_table 两种汇总方式。

**核心知识点**

- groupby 按列分组后调用 agg 可对不同列应用不同聚合函数
- agg 支持传入字典，为每列指定各自的统计方式
- pivot_table 生成交叉表，参数包括 index、columns、values、aggfunc
- 透视表的 margins 可输出合计行或合计列

**示例**

```python
g = df.groupby('dept').agg({'amount': ['sum', 'mean'], 'order_no': 'count'})
pt = pd.pivot_table(df, index='dept', columns='month', values='amount', aggfunc='sum', margins=True)
```

**易错点**：分组键存在缺失值时会默认被丢弃，需要时设置 dropna=False 保留。

---

## 第三章 分析与可视化

本章把处理好的数据变成结论与图形。

### 3.1 描述性统计

**学习目标**：会用统计量快速把握数据分布。

**核心知识点**

- describe 输出计数、均值、标准差与四分位数，适合快速摸底
- 关注中位数与均值差异可判断分布是否偏斜
- 相关性用 corr 计算，相关系数反映线性关系强弱而非因果
- 分位数与箱线图有助于发现异常值

**示例**

```python
print(df['amount'].describe())
print(df[['amount', 'count']].corr())
```

**易错点**：用相关系数直接推断因果关系，是数据分析中最常见的误用。

### 3.2 合并与连接

**学习目标**：掌握 merge 与 concat 的使用与差异。

**核心知识点**

- merge 类似 SQL 的 JOIN，how 可取 inner、left、right、outer
- on 指定连接键，两表列名不同时用 left_on 与 right_on
- concat 沿轴拼接，axis=0 纵向堆叠行，axis=1 横向拼接列
- 连接后应检查行数变化，避免因重复键造成数据膨胀

**示例**

```python
res = pd.merge(orders, users, left_on='user_id', right_on='id', how='left')
print(len(orders), len(res))   # 行数明显增多说明存在重复键
```

**易错点**：右表连接键有重复值时，left join 会导致结果行数膨胀，汇总金额被成倍放大。

### 3.3 图表绘制入门

**学习目标**：能画出常用的分析图表。

**核心知识点**

- Pandas 内置 plot 可直接基于 DataFrame 绘图，底层依赖 Matplotlib
- kind 参数选择图形：line 折线、bar 柱状、barh 条形、hist 直方、scatter 散点、pie 饼图
- 中文显示需设置字体，否则会出现方框
- 图表应添加标题与坐标轴标签，保证他人能独立读懂

**示例**

```python
import matplotlib.pyplot as plt
plt.rcParams['font.sans-serif'] = ['SimHei']
df.groupby('month')['amount'].sum().plot(kind='bar', title='月度销售额')
plt.tight_layout()
plt.show()
```

**易错点**：未设置中文字体导致标题与标签显示为方框，是 Python 绘图最常见的展示问题。

---

## 综合练习

### 单项选择题

**1. NumPy 数组相比 Python 列表的核心优势是？**

- A. 元素类型可以混合
- B. 支持向量化运算，性能更高
- C. 不需要导入库
- D. 自动去重

**答案**：B

**解析**：ndarray 元素类型统一、内存连续，支持向量化运算，避免了 Python 循环的逐元素开销。

**对应知识点**：NumPy 基础

**2. 在 Pandas 中按标签定位数据应使用？**

- A. iloc
- B. loc
- C. at 以外的索引
- D. values

**答案**：B

**解析**：loc 按索引标签定位，iloc 按整数位置定位，两者不可混用。

**对应知识点**：DataFrame 索引

**3. 统计各列缺失值数量的常用写法是？**

- A. df.count()
- B. df.isnull().sum()
- C. df.dropna()
- D. df.describe()

**答案**：B

**解析**：isnull() 生成布尔表，再 sum() 即可得到每列的缺失值个数，是清洗的第一步。

**对应知识点**：缺失值处理

**4. pd.to_datetime 中用于把无法解析的值置为 NaT 的参数是？**

- A. errors='coerce'
- B. errors='raise'
- C. dayfirst=True
- D. utc=True

**答案**：A

**解析**：errors='coerce' 会把无法解析的值转为 NaT 而不是抛异常，避免流程被脏数据中断。

**对应知识点**：类型转换

**5. 生成交叉汇总表应使用的函数是？**

- A. groupby
- B. merge
- C. pivot_table
- D. concat

**答案**：C

**解析**：pivot_table 通过 index、columns、values、aggfunc 生成交叉表，适合二维汇总展示。

**对应知识点**：透视表

### 判断题

**1. 两个相关系数很高的字段，可以据此断定存在因果关系。**

**答案**：错误

**解析**：错误。相关系数只反映线性相关强弱，不能推断因果关系，可能存在共同影响因素。

**对应知识点**：统计分析

**2. 使用 merge 进行左连接后行数明显增多，通常说明右表的连接键存在重复值。**

**答案**：正确

**解析**：正确。右表连接键重复会让每条左表记录匹配到多行，造成结果行数膨胀。

**对应知识点**：数据合并

**3. 用均值填充缺失值是万能的，对所有分布形态的数据都合适。**

**答案**：错误

**解析**：错误。均值填充会改变数据分布并削弱方差，偏态分布更适合用中位数或结合业务规则填充。

**对应知识点**：缺失值填充

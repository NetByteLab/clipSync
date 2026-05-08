package com.clipsync.app.ime

data class PinyinCandidate(
    val text: String,
    val sourcePinyin: String,
    val score: Int
)

data class CandidatePage(
    val items: List<PinyinCandidate>,
    val pageIndex: Int,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean
)

object PinyinCandidateEngine {

    private var learnedWeights: Map<String, Int> = emptyMap()
    private var pinnedPhrases: Map<String, String> = emptyMap()

    private val fuzzyGroups = listOf(
        setOf("z", "zh"),
        setOf("c", "ch"),
        setOf("s", "sh"),
        setOf("l", "n"),
        setOf("f", "h"),
        setOf("r", "l"),
        setOf("an", "ang"),
        setOf("en", "eng"),
        setOf("in", "ing"),
        setOf("ian", "iang"),
        setOf("uan", "uang")
    )

    private val entries: List<PinyinEntry> = buildList {
        addEntry("a", 960, "啊", "阿", "呵")
        addEntry("ai", 950, "爱", "矮", "挨", "哎")
        addEntry("an", 940, "安", "按", "案", "俺")
        addEntry("ba", 930, "把", "吧", "八", "爸")
        addEntry("bai", 920, "白", "百", "拜")
        addEntry("ban", 915, "办", "半", "班", "版")
        addEntry("bang", 910, "帮", "棒", "绑")
        addEntry("bao", 905, "包", "报", "保", "宝")
        addEntry("bei", 900, "被", "北", "倍", "备")
        addEntry("ben", 895, "本", "奔")
        addEntry("bi", 890, "比", "必", "笔", "闭")
        addEntry("bian", 885, "边", "变", "便", "编")
        addEntry("bie", 880, "别")
        addEntry("bing", 875, "并", "病", "兵")
        addEntry("bo", 870, "不", "波", "播")
        addEntry("bu", 865, "不", "部", "步")
        addEntry("ca", 860, "擦")
        addEntry("cai", 855, "才", "菜", "猜")
        addEntry("can", 850, "参", "残")
        addEntry("cang", 845, "藏", "仓")
        addEntry("cao", 840, "草", "操")
        addEntry("ce", 835, "测", "册")
        addEntry("ceng", 830, "层")
        addEntry("cha", 825, "查", "差", "茶", "插")
        addEntry("chai", 820, "拆", "柴")
        addEntry("chan", 815, "产", "单", "缠")
        addEntry("chang", 810, "长", "常", "唱", "场")
        addEntry("chao", 805, "超", "朝", "吵")
        addEntry("che", 800, "车", "彻")
        addEntry("chen", 795, "陈", "沉", "晨")
        addEntry("cheng", 790, "成", "程", "城", "称")
        addEntry("chi", 785, "吃", "持", "迟")
        addEntry("chong", 780, "充", "冲", "重")
        addEntry("chou", 775, "抽", "丑", "愁")
        addEntry("chu", 770, "出", "处", "初")
        addEntry("chuang", 765, "窗", "床", "创")
        addEntry("ci", 760, "此", "次", "词")
        addEntry("cong", 755, "从", "聪")
        addEntry("cuo", 750, "错")
        addEntry("da", 745, "大", "打", "答", "达")
        addEntry("dai", 740, "带", "代", "待")
        addEntry("dan", 735, "但", "单", "担", "蛋")
        addEntry("dang", 730, "当", "党", "挡")
        addEntry("dao", 725, "到", "道", "倒", "导")
        addEntry("de", 720, "的", "得", "德")
        addEntry("deng", 715, "等", "灯", "登")
        addEntry("di", 710, "地", "第", "低", "的")
        addEntry("dian", 705, "点", "电", "店", "典")
        addEntry("ding", 700, "定", "顶")
        addEntry("dong", 695, "东", "动", "懂")
        addEntry("dou", 690, "都", "斗", "豆")
        addEntry("du", 685, "度", "读", "独", "都")
        addEntry("dui", 680, "对", "队")
        addEntry("duo", 675, "多", "夺")
        addEntry("e", 670, "饿", "额", "俄")
        addEntry("en", 665, "嗯", "恩")
        addEntry("er", 660, "而", "二", "儿")
        addEntry("fa", 655, "发", "法")
        addEntry("fan", 650, "反", "饭", "翻")
        addEntry("fang", 645, "方", "放", "房")
        addEntry("fei", 640, "非", "飞", "费")
        addEntry("fen", 635, "分", "份", "粉")
        addEntry("feng", 630, "风", "封", "丰")
        addEntry("fo", 625, "佛")
        addEntry("fou", 620, "否")
        addEntry("fu", 615, "服", "复", "福", "父")
        addEntry("gai", 610, "该", "改", "概")
        addEntry("gan", 605, "干", "感", "赶")
        addEntry("gang", 600, "刚", "港", "钢")
        addEntry("gao", 595, "高", "告", "搞")
        addEntry("ge", 590, "个", "各", "歌", "格")
        addEntry("gei", 585, "给")
        addEntry("gen", 580, "跟", "根")
        addEntry("geng", 575, "更", "耕")
        addEntry("gong", 570, "工", "公", "共", "功")
        addEntry("gou", 565, "够", "构", "狗")
        addEntry("gu", 560, "古", "故", "顾")
        addEntry("gua", 555, "挂", "瓜")
        addEntry("guan", 550, "关", "管", "观")
        addEntry("guang", 545, "光", "广")
        addEntry("gui", 540, "贵", "归", "鬼")
        addEntry("guo", 535, "国", "过", "果")
        addEntry("ha", 530, "哈")
        addEntry("hai", 525, "还", "海", "害")
        addEntry("han", 520, "汉", "汗", "喊")
        addEntry("hang", 515, "行", "航")
        addEntry("hao", 510, "好", "号", "毫", "浩")
        addEntry("he", 505, "和", "喝", "合", "河")
        addEntry("hen", 500, "很", "狠")
        addEntry("hong", 495, "红", "宏", "洪")
        addEntry("hou", 490, "后", "候", "厚")
        addEntry("hu", 485, "湖", "户", "护", "呼")
        addEntry("hua", 480, "话", "花", "化", "华")
        addEntry("huan", 475, "还", "换", "欢")
        addEntry("huang", 470, "黄", "皇", "慌")
        addEntry("hui", 465, "会", "回", "灰", "汇")
        addEntry("huo", 460, "或", "活", "火")
        addEntry("ji", 455, "机", "几", "级", "记", "集")
        addEntry("jia", 450, "家", "加", "价", "假")
        addEntry("jian", 445, "见", "建", "间", "件")
        addEntry("jiang", 440, "将", "讲", "江", "降")
        addEntry("jiao", 435, "叫", "教", "角", "交")
        addEntry("jie", 430, "接", "节", "界", "解")
        addEntry("jin", 425, "进", "今", "近", "金")
        addEntry("jing", 420, "经", "京", "景", "静")
        addEntry("jiu", 415, "就", "九", "久")
        addEntry("ju", 410, "就", "句", "局", "具")
        addEntry("jue", 405, "觉", "决", "绝")
        addEntry("jun", 400, "军", "均")
        addEntry("kai", 395, "开", "凯")
        addEntry("kan", 390, "看", "刊")
        addEntry("kao", 385, "考", "靠")
        addEntry("ke", 380, "可", "科", "客", "课")
        addEntry("kong", 375, "空", "控")
        addEntry("kuai", 370, "快", "块")
        addEntry("la", 365, "啦", "拉")
        addEntry("lai", 360, "来", "赖")
        addEntry("lan", 355, "兰", "蓝", "栏")
        addEntry("lang", 350, "浪", "郎")
        addEntry("lao", 345, "老", "劳")
        addEntry("le", 340, "了", "乐")
        addEntry("lei", 335, "类", "累", "雷")
        addEntry("li", 330, "里", "理", "力", "立", "例")
        addEntry("lian", 325, "连", "脸", "练", "联")
        addEntry("liang", 320, "两", "量", "亮", "良")
        addEntry("liao", 315, "了", "料", "聊")
        addEntry("lin", 310, "林", "临")
        addEntry("ling", 305, "领", "令", "零", "灵")
        addEntry("liu", 300, "六", "流", "留")
        addEntry("long", 295, "龙", "隆", "弄")
        addEntry("lou", 290, "楼", "漏")
        addEntry("lu", 285, "路", "录", "陆")
        addEntry("lv", 280, "绿", "旅", "率")
        addEntry("ma", 275, "吗", "妈", "马", "嘛")
        addEntry("mai", 270, "买", "卖", "麦")
        addEntry("man", 265, "慢", "满", "曼")
        addEntry("mang", 260, "忙", "芒")
        addEntry("mao", 255, "毛", "猫", "贸")
        addEntry("me", 250, "么")
        addEntry("mei", 245, "没", "每", "美", "妹")
        addEntry("men", 240, "们", "门")
        addEntry("meng", 235, "梦", "蒙")
        addEntry("mi", 230, "米", "迷", "密")
        addEntry("mian", 225, "面", "免", "棉")
        addEntry("ming", 220, "名", "明", "命")
        addEntry("mo", 215, "摸", "模", "末", "默")
        addEntry("mu", 210, "目", "母", "木")
        addEntry("na", 205, "那", "哪", "拿", "呢")
        addEntry("nai", 200, "乃", "奶")
        addEntry("nan", 195, "南", "难", "男")
        addEntry("nao", 190, "脑", "闹")
        addEntry("ne", 185, "呢")
        addEntry("nei", 180, "内")
        addEntry("neng", 175, "能")
        addEntry("ni", 170, "你", "呢", "尼", "泥")
        addEntry("nian", 165, "年", "念", "粘")
        addEntry("nin", 160, "您")
        addEntry("ning", 155, "宁", "凝")
        addEntry("niu", 150, "牛", "扭")
        addEntry("nong", 145, "农", "弄")
        addEntry("nu", 140, "努", "怒")
        addEntry("nv", 135, "女")
        addEntry("o", 130, "哦")
        addEntry("ou", 125, "欧", "偶")
        addEntry("pa", 120, "怕", "爬")
        addEntry("pai", 118, "派", "排")
        addEntry("pan", 116, "盘", "判")
        addEntry("pang", 114, "旁", "胖")
        addEntry("pao", 112, "跑", "泡")
        addEntry("pei", 110, "配", "陪")
        addEntry("peng", 108, "朋", "碰")
        addEntry("pi", 106, "批", "皮", "匹")
        addEntry("pian", 104, "片", "篇", "偏")
        addEntry("piao", 102, "票", "漂")
        addEntry("pin", 100, "拼", "品")
        addEntry("ping", 98, "平", "评", "瓶")
        addEntry("po", 96, "破", "坡")
        addEntry("pu", 94, "普", "铺")
        addEntry("qi", 92, "起", "其", "气", "期", "器")
        addEntry("qian", 90, "前", "钱", "千", "签")
        addEntry("qiang", 88, "强", "枪", "墙")
        addEntry("qiao", 86, "桥", "敲", "巧")
        addEntry("qie", 84, "切", "且")
        addEntry("qin", 82, "亲", "勤", "琴")
        addEntry("qing", 80, "请", "情", "青", "清")
        addEntry("qiu", 78, "求", "球", "秋")
        addEntry("qu", 76, "去", "区", "取", "曲")
        addEntry("quan", 74, "全", "权", "圈")
        addEntry("que", 72, "却", "缺")
        addEntry("qun", 70, "群")
        addEntry("ran", 68, "然", "燃")
        addEntry("rang", 66, "让", "嚷")
        addEntry("re", 64, "热")
        addEntry("ren", 62, "人", "认", "任")
        addEntry("ri", 60, "日")
        addEntry("rong", 58, "容", "荣")
        addEntry("ru", 56, "如", "入")
        addEntry("ruan", 54, "软")
        addEntry("rui", 52, "瑞")
        addEntry("ruo", 50, "若")
        addEntry("sa", 48, "撒")
        addEntry("sai", 46, "赛")
        addEntry("san", 44, "三", "散")
        addEntry("sao", 42, "扫")
        addEntry("se", 40, "色")
        addEntry("sha", 38, "啥", "杀", "沙")
        addEntry("shan", 36, "山", "闪", "善")
        addEntry("shang", 34, "上", "商", "伤")
        addEntry("shao", 32, "少", "烧")
        addEntry("she", 30, "设", "社", "蛇")
        addEntry("shen", 28, "什", "深", "神", "身")
        addEntry("sheng", 26, "生", "声", "省", "胜")
        addEntry("shi", 24, "是", "时", "事", "使", "十")
        addEntry("shou", 22, "手", "收", "受")
        addEntry("shu", 20, "书", "数", "树", "输")
        addEntry("shui", 18, "水", "谁")
        addEntry("shuo", 16, "说", "硕")
        addEntry("si", 14, "四", "思", "死")
        addEntry("song", 12, "送", "松")
        addEntry("sou", 10, "搜")
        addEntry("su", 8, "苏", "诉", "素")
        addEntry("suan", 6, "算")
        addEntry("sui", 4, "随", "岁", "虽")
        addEntry("suo", 2, "所", "锁")
        addEntry("ta", 980, "他", "她", "它", "塔")
        addEntry("tai", 978, "太", "台")
        addEntry("tan", 976, "谈", "探", "坦")
        addEntry("tang", 974, "唐", "堂", "糖")
        addEntry("tao", 972, "套", "讨", "桃")
        addEntry("te", 970, "特")
        addEntry("teng", 968, "疼")
        addEntry("ti", 966, "体", "提", "题", "替")
        addEntry("tian", 964, "天", "田", "填")
        addEntry("tiao", 962, "条", "跳")
        addEntry("ting", 960, "听", "停", "庭")
        addEntry("tong", 958, "同", "通", "痛")
        addEntry("tou", 956, "头", "投")
        addEntry("tu", 954, "图", "土", "途")
        addEntry("tui", 952, "推", "退")
        addEntry("tuo", 950, "托", "脱")
        addEntry("wa", 948, "哇", "瓦", "挖")
        addEntry("wai", 946, "外")
        addEntry("wan", 944, "万", "完", "晚", "玩")
        addEntry("wang", 942, "网", "王", "望", "往")
        addEntry("wei", 940, "为", "位", "未", "维", "微")
        addEntry("wen", 938, "问", "文", "闻")
        addEntry("wo", 936, "我", "握", "窝")
        addEntry("wu", 934, "无", "五", "物", "务", "误")
        addEntry("xi", 932, "喜", "系", "西", "息", "希")
        addEntry("xia", 930, "下", "夏", "吓")
        addEntry("xian", 928, "先", "现", "线", "显")
        addEntry("xiang", 926, "想", "向", "像", "相")
        addEntry("xiao", 924, "小", "笑", "校", "消")
        addEntry("xie", 922, "写", "些", "谢", "协")
        addEntry("xin", 920, "新", "心", "信", "辛")
        addEntry("xing", 918, "行", "性", "型", "兴")
        addEntry("xiong", 916, "兄", "雄")
        addEntry("xiu", 914, "修", "秀")
        addEntry("xu", 912, "许", "需", "续")
        addEntry("xuan", 910, "选", "宣", "旋")
        addEntry("xue", 908, "学", "雪", "血")
        addEntry("xun", 906, "寻", "讯", "迅")
        addEntry("ya", 904, "呀", "压", "亚")
        addEntry("yan", 902, "眼", "言", "验", "烟")
        addEntry("yang", 900, "样", "阳", "养", "杨")
        addEntry("yao", 898, "要", "摇", "药")
        addEntry("ye", 896, "也", "页", "夜")
        addEntry("yi", 894, "一", "已", "以", "意", "议")
        addEntry("yin", 892, "因", "音", "银", "印")
        addEntry("ying", 890, "应", "英", "影", "营")
        addEntry("yong", 888, "用", "永", "勇")
        addEntry("you", 886, "有", "又", "由", "友")
        addEntry("yu", 884, "于", "与", "语", "鱼", "雨")
        addEntry("yuan", 882, "元", "原", "员", "远")
        addEntry("yue", 880, "月", "越", "约")
        addEntry("yun", 878, "云", "运")
        addEntry("zai", 876, "在", "再", "载")
        addEntry("zan", 874, "咱", "赞")
        addEntry("zao", 872, "早", "造")
        addEntry("ze", 870, "则", "责")
        addEntry("zen", 868, "怎")
        addEntry("zeng", 866, "曾", "增")
        addEntry("zha", 864, "炸", "扎", "查")
        addEntry("zhan", 862, "站", "战", "展")
        addEntry("zhang", 860, "长", "张", "章", "掌")
        addEntry("zhao", 858, "找", "照", "着", "招")
        addEntry("zhe", 856, "这", "着", "者")
        addEntry("zhen", 854, "真", "针", "镇")
        addEntry("zheng", 852, "正", "政", "整", "证")
        addEntry("zhi", 850, "知", "只", "之", "直", "指")
        addEntry("zhong", 848, "中", "种", "重", "众")
        addEntry("zhou", 846, "周", "州", "走")
        addEntry("zhu", 844, "主", "住", "注", "祝")
        addEntry("zhuan", 842, "转", "专")
        addEntry("zhuang", 840, "装", "状")
        addEntry("zhui", 838, "追")
        addEntry("zhun", 836, "准")
        addEntry("zhuo", 834, "着", "桌")
        addEntry("zi", 832, "子", "字", "自")
        addEntry("zong", 830, "总", "宗")
        addEntry("zou", 828, "走")
        addEntry("zu", 826, "组", "足", "族")
        addEntry("zui", 824, "最", "嘴")
        addEntry("zuo", 822, "做", "作", "坐", "左")
        addEntry("nihao", 1400, "你好")
        addEntry("xiexie", 1390, "谢谢")
        addEntry("zaijian", 1380, "再见")
        addEntry("women", 1370, "我们")
        addEntry("nimen", 1360, "你们")
        addEntry("tamen", 1350, "他们", "她们", "它们")
        addEntry("zhongguo", 1340, "中国")
        addEntry("renmin", 1330, "人民")
        addEntry("gongzuo", 1320, "工作")
        addEntry("shenghuo", 1310, "生活")
        addEntry("xianzai", 1300, "现在")
        addEntry("jinian", 1290, "今年")
        addEntry("jintian", 1280, "今天")
        addEntry("mingtian", 1270, "明天")
        addEntry("zuotian", 1260, "昨天")
        addEntry("keyi", 1250, "可以")
        addEntry("meiguanxi", 1240, "没关系")
        addEntry("meishenme", 1230, "没什么")
        addEntry("duibuqi", 1220, "对不起")
        addEntry("bukeqi", 1210, "不客气")
        addEntry("zhidao", 1200, "知道")
        addEntry("xihuan", 1190, "喜欢")
        addEntry("gaoxing", 1180, "高兴")
        addEntry("pengyou", 1170, "朋友")
        addEntry("laoshi", 1160, "老师")
        addEntry("xuesheng", 1150, "学生")
        addEntry("shouji", 1140, "手机")
        addEntry("diannao", 1130, "电脑")
        addEntry("wangluo", 1120, "网络")
        addEntry("shurufa", 1110, "输入法")
        addEntry("shurufa", 1100, "输入法的")
        addEntry("nihaoma", 1090, "你好吗")
        addEntry("zaoshanghao", 1080, "早上好")
        addEntry("wanshanghao", 1070, "晚上好")
        addEntry("qingwen", 1060, "请问")
        addEntry("meiwenti", 1050, "没问题")
        addEntry("zenmeyang", 1040, "怎么样")
        addEntry("haojiu", 1030, "好久")
        addEntry("haojiubujian", 1020, "好久不见")
        addEntry("henhaochi", 1010, "很好吃")
        addEntry("henhaokan", 1000, "很好看")
        addEntry("gongzuoshunli", 990, "工作顺利")
        addEntry("wananshui", 980, "晚安")
    }

    private val exactIndex: Map<String, List<PinyinEntry>> = entries.groupBy { it.pinyin }

    fun getCandidates(pinyin: String, limit: Int = 12): List<String> {
        return getCandidatePage(pinyin = pinyin, pageIndex = 0, pageSize = limit).items.map { it.text }
    }

    fun setLearnedWeights(weights: Map<String, Int>) {
        learnedWeights = weights
    }

    fun setPinnedPhrases(phrases: Map<String, String>) {
        pinnedPhrases = phrases
    }

    fun getCandidatePage(
        pinyin: String,
        pageIndex: Int = 0,
        pageSize: Int = 12
    ): CandidatePage {
        val normalized = normalize(pinyin)
        if (normalized.isBlank()) {
            return CandidatePage(
                items = emptyList(),
                pageIndex = 0,
                totalPages = 0,
                hasPrevious = false,
                hasNext = false
            )
        }

        val ranked = linkedMapOf<String, PinyinCandidate>()
        val fuzzyForms = expandFuzzy(normalized)

        pinnedPhrases[normalized]?.let { pinned ->
            mergeCandidate(
                ranked = ranked,
                text = pinned,
                sourcePinyin = normalized,
                score = PINNED_PHRASE_SCORE
            )
        }

        fuzzyForms.forEach { query ->
            val exactEntries = exactIndex[query].orEmpty()
            exactEntries.forEach { entry ->
                entry.words.forEachIndexed { index, word ->
                    val score = entry.baseScore +
                        exactBonus(query, normalized) -
                        index * 18 +
                        learnedBonus(entry.pinyin, word)
                    mergeCandidate(ranked, word, entry.pinyin, score)
                }
            }

            exactIndex.asSequence()
                .filter { (key, _) -> key.startsWith(query) && key != query }
                .forEach { (key, values) ->
                    val prefixPenalty = (key.length - query.length) * 40
                    values.forEach { entry ->
                        entry.words.forEachIndexed { index, word ->
                            val score = entry.baseScore -
                                prefixPenalty -
                                index * 15 -
                                fuzzyPenalty(query, normalized) +
                                learnedBonus(entry.pinyin, word)
                            mergeCandidate(ranked, word, entry.pinyin, score)
                    }
                }
            }
        }

        buildSegmentedCandidates(normalized).forEach { candidate ->
            mergeCandidate(
                ranked = ranked,
                text = candidate.text,
                sourcePinyin = candidate.sourcePinyin,
                score = candidate.score
            )
        }

        val rankedList = ranked.values
            .sortedWith(
                compareByDescending<PinyinCandidate> { it.score }
                    .thenByDescending { phraseBonus(it.text) }
                    .thenByDescending { it.text.length }
                    .thenBy { it.text }
            )

        if (rankedList.isEmpty()) {
            return CandidatePage(
                items = listOf(PinyinCandidate(text = normalized, sourcePinyin = normalized, score = 0)),
                pageIndex = 0,
                totalPages = 1,
                hasPrevious = false,
                hasNext = false
            )
        }

        val safePageSize = pageSize.coerceAtLeast(1)
        val totalPages = ((rankedList.size + safePageSize - 1) / safePageSize).coerceAtLeast(1)
        val safePageIndex = pageIndex.coerceIn(0, totalPages - 1)
        val start = safePageIndex * safePageSize
        val end = minOf(start + safePageSize, rankedList.size)

        return CandidatePage(
            items = rankedList.subList(start, end),
            pageIndex = safePageIndex,
            totalPages = totalPages,
            hasPrevious = safePageIndex > 0,
            hasNext = safePageIndex < totalPages - 1
        )
    }

    fun normalize(pinyin: String): String {
        return pinyin.lowercase()
            .replace("ü", "v")
            .replace("u:", "v")
            .filter { it.isLetter() }
    }

    private fun expandFuzzy(pinyin: String): Set<String> {
        val results = linkedSetOf(pinyin)
        fuzzyGroups.forEach { group ->
            val snapshot = results.toList()
            snapshot.forEach { value ->
                group.forEach { token ->
                    if (value.contains(token)) {
                        group.forEach { replacement ->
                            if (replacement != token) {
                                results += value.replaceFirst(token, replacement)
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    private fun exactBonus(candidateQuery: String, userQuery: String): Int {
        return when {
            candidateQuery == userQuery -> 800
            candidateQuery.startsWith(userQuery) -> 420
            else -> 250
        }
    }

    private fun learnedBonus(pinyin: String, text: String): Int {
        return (learnedWeights["$pinyin|$text"] ?: 0) * LEARNED_WEIGHT_STEP
    }

    private fun buildSegmentedCandidates(pinyin: String): List<PinyinCandidate> {
        if (pinyin.length < 4) return emptyList()

        val combinations = segmentPinyin(pinyin)
        val results = mutableListOf<PinyinCandidate>()

        combinations.forEach { segments ->
            if (segments.size < 2) return@forEach

            for (takeCount in 2..segments.size) {
                val consumedSegments = segments.take(takeCount)
                val candidate = buildCandidateFromSegments(
                    segments = consumedSegments,
                    fullInput = pinyin,
                    allowTrailingRemainder = takeCount < segments.size
                )
                if (candidate != null) {
                    results += candidate
                }
            }
        }

        return results
    }

    private fun buildCandidateFromSegments(
        segments: List<String>,
        fullInput: String,
        allowTrailingRemainder: Boolean
    ): PinyinCandidate? {
        val chosenWords = mutableListOf<String>()
        var totalScore = 0

        segments.forEachIndexed { index, segment ->
            val bestEntry = exactIndex[segment]
                .orEmpty()
                .maxByOrNull { entry -> entry.baseScore }
                ?: return null
            val bestWord = bestEntry.words.firstOrNull() ?: return null
            chosenWords += bestWord
            totalScore += bestEntry.baseScore - index * 22 + learnedBonus(segment, bestWord)
        }

        val consumedPinyin = segments.joinToString(separator = "")
        val trailingRemainder = fullInput.removePrefix(consumedPinyin)
        val remainderPenalty = if (allowTrailingRemainder && trailingRemainder.isNotEmpty()) 110 else 0

        return PinyinCandidate(
            text = chosenWords.joinToString(separator = ""),
            sourcePinyin = segments.joinToString(separator = "'"),
            score = totalScore + segmentedPhraseBonus(segments) - remainderPenalty
        )
    }

    private fun segmentPinyin(pinyin: String): List<List<String>> {
        val memo = mutableMapOf<Int, List<List<String>>>()

        fun dfs(start: Int): List<List<String>> {
            memo[start]?.let { return it }
            if (start == pinyin.length) {
                return listOf(emptyList())
            }

            val results = mutableListOf<List<String>>()
            val upperBound = minOf(pinyin.length, start + MAX_SEGMENT_LENGTH)
            for (end in start + 1..upperBound) {
                val segment = pinyin.substring(start, end)
                if (!exactIndex.containsKey(segment)) continue

                dfs(end).forEach { suffix ->
                    results += listOf(segment) + suffix
                }
            }

            return results.sortedWith(
                compareBy<List<String>> { it.size }
                    .thenBy { segments -> segments.sumOf { it.length } }
            ).take(MAX_SEGMENT_COMBINATIONS).also {
                memo[start] = it
            }
        }

        return dfs(0)
    }

    private fun fuzzyPenalty(candidateQuery: String, userQuery: String): Int {
        return if (candidateQuery == userQuery) 0 else 120
    }

    private fun phraseBonus(text: String): Int {
        return when {
            text.length >= 4 -> 80
            text.length == 3 -> 45
            text.length == 2 -> 20
            else -> 0
        }
    }

    private fun segmentedPhraseBonus(segments: List<String>): Int {
        val segmentCountBonus = when (segments.size) {
            2 -> 140
            3 -> 90
            else -> 40
        }
        val syllableBonus = segments.fold(0) { total, segment ->
            total + if (segment.length >= 2) 12 else 0
        }
        return segmentCountBonus + syllableBonus
    }

    private fun mergeCandidate(
        ranked: MutableMap<String, PinyinCandidate>,
        text: String,
        sourcePinyin: String,
        score: Int
    ) {
        val current = ranked[text]
        if (current == null || score > current.score) {
            ranked[text] = PinyinCandidate(text = text, sourcePinyin = sourcePinyin, score = score)
        }
    }

    private fun MutableList<PinyinEntry>.addEntry(
        pinyin: String,
        baseScore: Int,
        vararg words: String
    ) {
        add(PinyinEntry(normalize(pinyin), words.toList(), baseScore))
    }

    private data class PinyinEntry(
        val pinyin: String,
        val words: List<String>,
        val baseScore: Int
    )

    private const val MAX_SEGMENT_LENGTH = 6
    private const val MAX_SEGMENT_COMBINATIONS = 8
    private const val LEARNED_WEIGHT_STEP = 1200
    private const val PINNED_PHRASE_SCORE = 20_000
}

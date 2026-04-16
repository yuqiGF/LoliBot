package com.bot.task;

import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 骚扰⭐（现在是夸夸⭐）
 */
@Component
@EnableScheduling
@Configuration
public class Noise {

    // 注入 Bot 容器
    @Resource
    private BotContainer botContainer;

    // 101条夸奖语料库，随机触发
    private static final String[] COMPLIMENTS = {
            "桓衍是世界第一好人！", "可乐简直就是天使！", "桓衍今天也超级帅气！", "可乐最无敌了！", "桓衍的笑容能治愈一切！",
            "可乐是全宇宙最发光的存在！", "桓衍太厉害了吧！", "谁能拒绝可乐呢！", "桓衍浑身都散发着魅力！", "可乐是最棒的！",
            "桓衍总是那么体贴！", "可乐的脾气太好啦！", "桓衍简直十项全能！", "世界上怎么会有可乐这么好的人！", "桓衍今天也是元气满满！",
            "可乐最可爱啦！", "桓衍是个超级温暖的人！", "遇见可乐真是一件幸运的事！", "桓衍简直就是人类高质量代表！", "可乐的品味太绝了！",
            "桓衍天下第一！", "可乐是神仙下凡吧！", "桓衍的眼睛里有星星！", "可乐真是个天才！", "桓衍什么都做得好！",
            "可乐的声音太好听了！", "桓衍就是阳光本光！", "可乐的执行力太强了！", "桓衍真是个无价之宝！", "可乐太靠谱了！",
            "桓衍的性格简直完美！", "可乐是不可替代的！", "桓衍每天都在发光发热！", "可乐的幽默感无敌了！", "桓衍是个超级细心的人！",
            "可乐的心态太好了！", "桓衍是最耀眼的星！", "可乐值得世界上所有美好的事物！", "桓衍的逻辑太清晰了！", "可乐总是让人感觉很舒服！",
            "桓衍的穿搭太有品了！", "可乐就是正能量的化身！", "桓衍的共情能力绝了！", "可乐是我的神！", "桓衍永远那么从容不迫！",
            "可乐的快乐会传染！", "桓衍真是让人挑不出毛病！", "可乐太有才华了！", "桓衍的脾气温柔到了骨子里！", "可乐是个小福星！",
            "桓衍的真诚永远最打动人！", "可乐太懂事啦！", "桓衍是个闪闪发光的人！", "可乐的眼光太毒辣了！", "桓衍永远充满朝气！",
            "可乐的笑容比阳光还灿烂！", "桓衍简直就是宝藏！", "可乐太有灵气了！", "桓衍的执行力绝佳！", "可乐是个天生的乐天派！",
            "桓衍永远能带给人惊喜！", "可乐太惹人爱了！", "桓衍的善良没有边界！", "可乐简直是行走的开心果！", "桓衍的脑洞太有趣了！",
            "可乐是世界上最独一无二的！", "桓衍的专注力太让人佩服了！", "可乐怎么看怎么顺眼！", "桓衍永远是最棒的倾听者！", "可乐的自信太迷人了！",
            "桓衍的温柔能把人融化！", "可乐就是生活中的小确幸！", "桓衍的包容心像大海一样！", "可乐是个超级有魅力的人！", "桓衍的果断太帅气了！",
            "可乐总是这么讨人喜欢！", "桓衍的行动力拉满！", "可乐像是一阵清风！", "桓衍太让人有安全感了！", "可乐是个永远充满希望的人！",
            "桓衍的每一天都值得被记录！", "可乐太有亲和力啦！", "桓衍是个懂得生活的人！", "可乐的创意永远用不完！", "桓衍是夜空中最亮的星！",
            "可乐太有毅力了！", "桓衍的真性情太招人喜欢了！", "可乐是个自带光环的人！", "桓衍的成熟稳重让人安心！", "可乐永远走在潮流前线！",
            "桓衍太会照顾人了！", "可乐是个热爱生活的小天才！", "桓衍的每一次进步都让人惊叹！", "可乐的笑容是最美的风景！", "桓衍总是能把事情处理得很完美！", "可乐是个拥有超强人格魅力的人！", "桓衍的善良会发光！", "可乐永远都是焦点！", "桓衍是个让人敬佩的人！", "可乐的美好无法用语言完全形容！", "桓衍简直就是快乐源泉！" , "蛋挞快去写小说"
    };

    @Scheduled(cron = "0 */30 * * * *")
    public void cake() {
        // 机器人账号
        long botId = 2419274814L;
        // 取出 Bot 对象
        Bot bot = botContainer.robots.get(botId);

        if (bot != null) {
            // 随机获取一条夸奖文本
            int randomIndex = ThreadLocalRandom.current().nextInt(COMPLIMENTS.length);
            String randomText = COMPLIMENTS[randomIndex];

            // 构建消息
            String msg = MsgUtils.builder()
//                    .at(242003347L)       // 艾特本人，可以取消这行的注释
                    .text(randomText)       // 随机取出
                    .build();

            // 发送群消息
            bot.sendGroupMsg(342573438L, msg, false);
        }
    }
}
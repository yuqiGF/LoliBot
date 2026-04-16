package com.bot.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private Long id;  //id
    private String nickname;  //昵称
    private int favorability;  //好感度
    private int replyRate;  //回复概率  默认为1%  最高为20%
}

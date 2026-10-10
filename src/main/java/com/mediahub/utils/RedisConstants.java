package com.mediahub.utils;

/** Redis 相关的 key 前缀与有效时常量 */
public class RedisConstants {
    /** 登录验证码缓存 key 前缀（后接手机号） */
    public static final String LOGIN_CODE_KEY = "login:code:";
    /** 登录验证码有效期（分钟） */
    public static final Long LOGIN_CODE_TTL = 2L;
    /** 登录用户 token 缓存 key 前缀（后接 token，存 hash） */
    public static final String LOGIN_USER_KEY = "login:token:";
    /** 登录用户 token 有效期（分钟） */
    public static final Long LOGIN_USER_TTL = 36000L;

    /** 缓存空值（防穿透）有效期（分钟） */
    public static final Long CACHE_NULL_TTL = 2L;

    /** 媒体缓存默认有效期（分钟） */
    public static final Long CACHE_MEDIA_TTL = 30L;
    /** 媒体缓存 key 前缀 */
    public static final String CACHE_MEDIA_KEY = "cache:media:";

    /** 媒体缓存重建互斥锁 key 前缀 */
    public static final String LOCK_MEDIA_KEY = "lock:media:";
    /** 互斥锁有效期（秒） */
    public static final Long LOCK_MEDIA_TTL = 10L;

    /** 秒杀库存 key 前缀 */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    /** 评论点赞集合 key 前缀 */
    public static final String COMMENT_LIKED_KEY = "comment:liked:";
    /** Feed 收件箱 key 前缀（后接用户id） */
    public static final String FEED_KEY = "feed:";
    /** 用户签到位图 key 前缀 */
    public static final String USER_SIGN_KEY = "sign:";
    /** UV（独立访客）统计 key 前缀（后接日期） */
    public static final String UV_KEY = "uv:";

    /** 热榜：条目热度 ZSet（member=条目id，score=热度分） */
    public static final String RANK_MEDIA_KEY = "rank:media";
    /** 热榜：评论热度 ZSet（member=评论id，score=热度分） */
    public static final String RANK_COMMENT_KEY = "rank:comment";
    /** 热榜缓存有效期（分钟），到期后由下次查询触发重建 */
    public static final Long RANK_TTL = 60L;
}

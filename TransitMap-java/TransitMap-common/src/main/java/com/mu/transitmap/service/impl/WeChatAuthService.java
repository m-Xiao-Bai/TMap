package com.mu.transitmap.service.impl;

import com.mu.transitmap.constants.Constants;
import com.mu.transitmap.entity.User;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.enums.UserGenderEnum;
import com.mu.transitmap.enums.UserRoleEnum;
import com.mu.transitmap.enums.UserStatusEnum;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.mapper.UserMapper;
import com.mu.transitmap.utils.JwtUtil;
import com.mu.transitmap.utils.RedisUtils;
import com.mu.transitmap.vo.UserLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class WeChatAuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RedisUtils redisUtils;
    private final SystemConfigServiceImpl systemConfigService;

    @Value("${wechat.appid:}")
    private String appId;

    @Value("${wechat.secret:}")
    private String appSecret;

    private static final String JSCODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    public WeChatAuthService(UserMapper userMapper, JwtUtil jwtUtil,
                             RedisUtils redisUtils, SystemConfigServiceImpl systemConfigService) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.redisUtils = redisUtils;
        this.systemConfigService = systemConfigService;
    }

    /**
     * 微信小程序登录：code 换 openid，查找或自动注册用户，返回登录 VO
     */
    @SuppressWarnings("unchecked")
    public UserLoginVO loginByWeChat(String code) {
        if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            log.error("微信小程序 appid/secret 未配置");
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED);
        }

        // 1. 调用微信 jscode2session 接口
        String openid;
        try {
            String url = String.format(JSCODE2SESSION_URL, appId, appSecret, code);
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);

            if (resp == null) {
                log.error("微信接口返回空响应");
                throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED);
            }
            if (resp.containsKey("errcode") && !Integer.valueOf(0).equals(resp.get("errcode"))) {
                log.error("微信接口错误: errcode={}, errmsg={}", resp.get("errcode"), resp.get("errmsg"));
                throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED);
            }

            openid = (String) resp.get("openid");
            if (openid == null || openid.isEmpty()) {
                log.error("微信接口未返回 openid");
                throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED);
            }
            log.info("微信登录 code 换取 openid 成功: {}", openid);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信 jscode2session 接口失败", e);
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED);
        }

        // 2. 查找或创建用户
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getWechatId, openid)
        );

        if (user == null) {
            user = new User();
            user.setWechatId(openid);
            user.setUsername("微信用户" + ThreadLocalRandom.current().nextInt(10000, 99999));
            user.setGenderCode(UserGenderEnum.UNKNOWN.getCode());
            user.setGender(UserGenderEnum.UNKNOWN.getDesc());
            user.setCountryId(Constants.ZEROES_L);
            user.setCountryName(Constants.WEI_ZHI);
            user.setCityId(Constants.ZEROES_L);
            user.setCityName(Constants.WEI_ZHI);
            user.setStatusCode(UserStatusEnum.ONLINE.getCode());
            user.setStatus(UserStatusEnum.ONLINE.getDesc());
            user.setRoleCode(UserRoleEnum.USER.getCode());
            user.setRole(UserRoleEnum.USER.getDesc());
            userMapper.insert(user);
            log.info("微信用户自动注册成功: userId={}, openid={}", user.getId(), openid);
        }

        // 3. 检查账号是否被禁用
        if (UserStatusEnum.DISABLED.getCode() == user.getStatusCode()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 4. 生成 JWT + 存 Redis
        String token = jwtUtil.generateToken(user.getId(), user.getRoleCode());
        long tokenExpiry = systemConfigService.getConfigInt("auth.token_expiry", (int) jwtUtil.getExpiration());
        redisUtils.storeToken(user.getId(), token, tokenExpiry);

        // 5. 更新在线状态
        user.setStatusCode(UserStatusEnum.ONLINE.getCode());
        user.setStatus(UserStatusEnum.ONLINE.getDesc());
        userMapper.updateById(user);

        // 6. 构建返回 VO
        UserLoginVO vo = new UserLoginVO();
        BeanUtils.copyProperties(user, vo);
        vo.setToken(token);
        return vo;
    }
}

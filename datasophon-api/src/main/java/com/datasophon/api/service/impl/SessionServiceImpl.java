/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package com.datasophon.api.service.impl;

import com.datasophon.api.service.SessionService;
import com.datasophon.api.utils.HttpUtils;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.SessionEntity;
import com.datasophon.dao.entity.UserInfoEntity;
import com.datasophon.dao.mapper.SessionMapper;

import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.WebUtils;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("sessionService")
public class SessionServiceImpl extends ServiceImpl<SessionMapper, SessionEntity> implements SessionService {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);
    
    @Autowired
    private SessionMapper sessionMapper;
    
    /**
     * get user session from request
     *
     * @param request request
     * @return session
     */
    @Override
    public SessionEntity getSession(HttpServletRequest request) {
        String sessionId = request.getHeader(Constants.SESSION_ID);
        
        if (StringUtils.isBlank(sessionId)) {
            Cookie cookie = WebUtils.getCookie(request, Constants.SESSION_ID);
            
            if (cookie != null) {
                sessionId = cookie.getValue();
            }
        }
        
        if (StringUtils.isBlank(sessionId)) {
            return null;
        }
        
        String ip = HttpUtils.getClientIpAddress(request);
        logger.debug("get session: {}, ip: {}", sessionId, ip);
        
        return sessionMapper.selectById(sessionId);
    }
    
    /**
     * create session
     *
     * @param user user
     * @param ip   ip
     * @return session string
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createSession(UserInfoEntity user, String ip) {
        SessionEntity session = null;
        
        // logined
        List<SessionEntity> sessionList = sessionMapper.queryByUserId(user.getId());
        
        Date now = new Date();
        
        /**
         * if you have logged in and are still valid, return directly
         */
        if (CollectionUtils.isNotEmpty(sessionList)) {
            // is session list greater 1 ， delete other ，get one
            if (sessionList.size() > 1) {
                for (int i = 1; i < sessionList.size(); i++) {
                    sessionMapper.deleteById(sessionList.get(i).getId());
                }
            }
            session = sessionList.get(0);
            if (now.getTime() - session.getLastLoginTime().getTime() <= Constants.SESSION_TIME_OUT * 1000) {
                /**
                 * updateProcessInstance the latest login time
                 */
                session.setLastLoginTime(now);
                sessionMapper.updateById(session);
                
                return session.getId();
                
            } else {
                /**
                 * session expired, then delete this session first
                 */
                sessionMapper.deleteById(session.getId());
            }
        }
        
        // assign new session
        session = new SessionEntity();
        
        session.setId(UUID.randomUUID().toString());
        session.setIp(ip);
        session.setUserId(user.getId());
        session.setLastLoginTime(now);
        
        sessionMapper.insertSession(session);
        
        return session.getId();
    }
    
    /**
     * sign out
     * remove ip restrictions
     *
     * @param ip        no use
     * @param loginUser login user
     */
    @Override
    public void signOut(String ip, UserInfoEntity loginUser) {
        try {
            /**
             * query session by user id and ip
             */
            SessionEntity session = sessionMapper.queryByUserIdAndIp(loginUser.getId(), ip);
            
            // delete session
            sessionMapper.deleteById(session.getId());
        } catch (Exception e) {
            logger.warn("userId : {} , ip : {} , find more one session", loginUser.getId(), ip);
        }
    }
    
}

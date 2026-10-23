package com.polaris.platform.controller;

import com.polaris.platform.service.CustomerServiceChannelService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅加载访客渠道的公开悬浮壳；宿主页面接触不到会话令牌、问题、答案或知识来源。 */
@RestController
@RequestMapping("/platform/runtime/customer-channels")
public class CustomerChannelWidgetController {
    private final CustomerServiceChannelService channels;
    public CustomerChannelWidgetController(CustomerServiceChannelService channels) { this.channels = channels; }
    @GetMapping(value = "/{code}/widget.js", produces = "text/javascript;charset=UTF-8")
    public String widget(@PathVariable String code, HttpServletResponse response) {
        var channel = channels.requireVisitorEntry(code);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        return """
            (()=>{'use strict';const script=document.currentScript;if(!script)return;
            const key='polaris-customer-%s';if(document.getElementById(key))return;
            const host=document.createElement('div');host.id=key;const root=host.attachShadow({mode:'closed'});
            const style=document.createElement('style');style.textContent=`
            :host{position:fixed;right:20px;bottom:20px;z-index:2147483000;font-family:system-ui,sans-serif}
            button{height:48px;padding:0 20px;border:0;border-radius:24px;background:#4f46e5;color:white;font:inherit;cursor:pointer;box-shadow:0 6px 24px #0003}
            section{position:absolute;right:0;bottom:60px;width:min(440px,calc(100vw - 24px));height:min(760px,calc(100dvh - 96px));border-radius:16px;overflow:hidden;box-shadow:0 12px 60px #0004;background:#fff}
            header{display:flex;justify-content:space-between;align-items:center;height:44px;padding:0 12px;background:#131c2c;color:white;font-size:14px}
            header button{height:32px;padding:0 12px;background:transparent;box-shadow:none}
            iframe{width:100%%;height:calc(100%% - 44px);border:0;display:block}
            @media(max-width:480px){:host{right:12px;bottom:max(12px,env(safe-area-inset-bottom))}}
            `;root.append(style);const toggle=document.createElement('button');toggle.textContent='在线客服';toggle.setAttribute('aria-expanded','false');root.append(toggle);
            let panel=null;const close=()=>{panel?.remove();panel=null;toggle.setAttribute('aria-expanded','false');toggle.focus()};
            toggle.addEventListener('click',()=>{if(panel){close();return}panel=document.createElement('section');panel.setAttribute('role','dialog');panel.setAttribute('aria-label','数字人客服');
            const header=document.createElement('header');header.append(document.createTextNode('数字人客服'));const exit=document.createElement('button');exit.textContent='关闭';exit.addEventListener('click',close);header.append(exit);
            const frame=document.createElement('iframe');frame.title='数字人客服接待';frame.src=new URL(script.src).href.replace(/widget\\.js(?:\\?.*)?$/,'embed');frame.setAttribute('sandbox','allow-scripts allow-same-origin allow-popups allow-popups-to-escape-sandbox');
            %s
            frame.referrerPolicy='same-origin';panel.append(header,frame);root.append(panel);toggle.setAttribute('aria-expanded','true');exit.focus()});
            host.addEventListener('keydown',event=>{if(event.key==='Escape'&&panel)close()});const mount=()=>{if(!document.getElementById(key))document.body.append(host)};if(document.body)mount();else document.addEventListener('DOMContentLoaded',mount,{once:true})})();
            """.formatted(code, Boolean.TRUE.equals(channel.getVisitorSpeechEnabled()) ? "frame.allow='microphone';" : "");
    }
}

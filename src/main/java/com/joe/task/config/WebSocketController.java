package com.joe.task.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.CrossOrigin;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

@ServerEndpoint("/websocket")
@Component

public class WebSocketController
{
    // 存储所有在线 WebSocket 客户端
    private static CopyOnWriteArraySet<WebSocketController> webSocketSet = new CopyOnWriteArraySet<>();

    private Session session;

    @OnOpen
    public void onOpen(Session session)
    {
        this.session = session;
        webSocketSet.add(this);

        System.out.println("1 session connected.......");
    }

    @OnClose
    public void onClose()
    {
        webSocketSet.remove(this);

        System.out.println("1 session closed .......");
    }

    @OnMessage
    public void onMessage(String message, Session session)
    {
        // 处理接收到的消息，如根据 message 中的数据从数据库中查询相应的数据
    }

    @OnError
    public void onError(Session session, Throwable error)
    {
        error.printStackTrace();
    }

    // 发送消息到前端
    public void sendMessageToFrontEnd(String message) throws IOException
    {
        this.session.getBasicRemote().sendText(message);
    }

    // 群发消息
    public static void sendMessage(String type, String jobInfo, String message)
    {
        System.out.println("webSocketSet.size() = " + webSocketSet.size());

        for (WebSocketController item : webSocketSet)
        {
            try
            {
                Map<String, String> map = new HashMap<>();
                map.put("type", type);
                map.put("content", message);
                map.put("jobInfo", jobInfo);

                ObjectMapper mapper = new ObjectMapper();
                String jsonResult = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);

                item.sendMessageToFrontEnd(jsonResult);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}
package ru.yandex.practicum.mymarket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import ru.yandex.practicum.mymarket.utils.Utils;

import java.io.IOException;

@Configuration
public class ContextClosedConfiguration {

    private final static String UPLOADS = "uploads/";

    @EventListener
    public void onContextClosed(ContextClosedEvent event) throws IOException {
        Utils.deleteDirectory(UPLOADS);
    }

}

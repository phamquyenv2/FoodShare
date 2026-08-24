package com.datn.foodshare.event;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.NotificationReferenceType;
import com.datn.foodshare.util.constant.NotificationType;
import lombok.Builder;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NotificationEvent extends ApplicationEvent {
    private final User user;
    private final String title;
    private final String content;
    private final NotificationType type;
    private final NotificationReferenceType referenceType;
    private final Long referenceId;

    @Builder
    public NotificationEvent(Object source, User user, String title, String content, 
                             NotificationType type, NotificationReferenceType referenceType, Long referenceId) {
        super(source);
        this.user = user;
        this.title = title;
        this.content = content;
        this.type = type;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }
}

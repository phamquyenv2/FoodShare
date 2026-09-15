package com.datn.foodshare.event;

import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.util.constant.NotificationChannel;
import com.datn.foodshare.util.constant.NotificationReferenceType;
import com.datn.foodshare.util.constant.NotificationType;
import lombok.Builder;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.EnumSet;
import java.util.Set;

@Getter
public class NotificationEvent extends ApplicationEvent {
    private final User user;
    private final String title;
    private final String content;
    private final NotificationType type;
    private final NotificationReferenceType referenceType;
    private final Long referenceId;
    private final Set<NotificationChannel> channels;

    @Builder
    public NotificationEvent(Object source, User user, String title, String content, 
                             NotificationType type, NotificationReferenceType referenceType, Long referenceId,
                             Set<NotificationChannel> channels) {
        super(source);
        this.user = user;
        this.title = title;
        this.content = content;
        this.type = type;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        EnumSet<NotificationChannel> resolvedChannels = channels == null || channels.isEmpty()
                ? EnumSet.noneOf(NotificationChannel.class)
                : EnumSet.copyOf(channels);
        resolvedChannels.add(NotificationChannel.IN_APP);
        this.channels = Set.copyOf(resolvedChannels);
    }

    public boolean supports(NotificationChannel channel) {
        return channels.contains(channel);
    }
}

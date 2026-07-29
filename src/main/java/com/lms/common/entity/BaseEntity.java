package com.lms.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Lớp cha cho toàn bộ 31 thực thể: khóa chính + audit timestamp.
 *
 * <p><b>Vì sao equals/hashCode chỉ dựa trên id:</b> nếu để Lombok {@code @Data} sinh
 * equals/hashCode trên mọi field, JPA sẽ nạp cả quan hệ LAZY khi so sánh hai entity
 * (gây {@code LazyInitializationException} ngoài transaction), và hai entity chưa
 * persist đều có mọi field null nên bị coi là bằng nhau. Vì vậy dự án
 * <b>KHÔNG dùng {@code @Data} trên entity</b>.
 *
 * <p>{@code hashCode()} trả về hằng số theo class thay vì hash của id: id được sinh
 * bởi database sau khi persist, nên nếu hash phụ thuộc id thì entity sẽ "biến mất"
 * khỏi {@code HashSet} ngay lúc được lưu. Đây là cách xử lý chuẩn cho JPA.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** {@code true} khi entity chưa được persist (chưa có id do DB sinh). */
    public boolean isNew() {
        return id == null;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        // Hibernate proxy có class con khác class thật -> phải so sánh class hiệu dụng
        Class<?> thisClass = this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : getClass();
        Class<?> otherClass = o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        if (!thisClass.equals(otherClass)) {
            return false;
        }
        BaseEntity other = (BaseEntity) o;
        // Hai entity chưa persist không bao giờ bằng nhau
        return id != null && id.equals(other.getId());
    }

    @Override
    public final int hashCode() {
        Class<?> effectiveClass = this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : getClass();
        return effectiveClass.hashCode();
    }
}

package com.datn.foodshare.repository.specification;

import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.request.FoodPostFilterRequest;
import com.datn.foodshare.util.constant.PostStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FoodPostSpecification {

    public static Specification<FoodPost> filterBy(FoodPostFilterRequest filter, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("postStatus"), PostStatus.AVAILABLE));
            predicates.add(cb.greaterThan(root.get("expiresAt"), now));

            if (filter != null) {
                if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                    String pattern = "%" + filter.getKeyword().trim().toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("name")), pattern),
                            cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern)
                    ));
                }
                if (filter.getCategoryId() != null) {
                    predicates.add(cb.equal(root.get("category").get("id"), filter.getCategoryId()));
                }
                if (filter.getPostType() != null) {
                    predicates.add(cb.equal(root.get("postType"), filter.getPostType()));
                }
                if (filter.getMinPrice() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("unitPrice"), filter.getMinPrice()));
                }
                if (filter.getMaxPrice() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("unitPrice"), filter.getMaxPrice()));
                }
                if (filter.getMinAvailableQuantity() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("availableQuantity"), filter.getMinAvailableQuantity()));
                }
                if (filter.getExpiresFrom() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("expiresAt"), filter.getExpiresFrom()));
                }
                if (filter.getExpiresTo() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("expiresAt"), filter.getExpiresTo()));
                }
            }

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("businessProfile", JoinType.INNER);
                root.fetch("category", JoinType.INNER);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

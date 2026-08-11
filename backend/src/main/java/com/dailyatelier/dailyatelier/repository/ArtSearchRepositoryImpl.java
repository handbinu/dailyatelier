package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.dto.ArtSearchCriteria;
import com.dailyatelier.dailyatelier.dto.ArtSearchSort;
import com.dailyatelier.dailyatelier.dto.ArtSearchStatus;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Artist;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class ArtSearchRepositoryImpl implements ArtSearchRepository {
    private final EntityManager entityManager;

    @Override
    public Page<Art> search(
            ArtSearchCriteria criteria,
            LocalDateTime now,
            Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Art> query = builder.createQuery(Art.class);
        Root<Art> art = query.from(Art.class);
        Join<Art, Artist> artist = art.join("artist");
        query.where(predicates(builder, art, artist, criteria, now)
                .toArray(Predicate[]::new));
        query.orderBy(orders(builder, art, criteria.sort(), now));

        TypedQuery<Art> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<Art> countArt = countQuery.from(Art.class);
        Join<Art, Artist> countArtist = countArt.join("artist");
        countQuery.select(builder.count(countArt));
        countQuery.where(predicates(
                builder, countArt, countArtist, criteria, now
        ).toArray(Predicate[]::new));

        return new PageImpl<>(
                typedQuery.getResultList(),
                pageable,
                entityManager.createQuery(countQuery).getSingleResult()
        );
    }

    private List<Predicate> predicates(
            CriteriaBuilder builder,
            Root<Art> art,
            Join<Art, Artist> artist,
            ArtSearchCriteria criteria,
            LocalDateTime now) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.notEqual(
                art.get("artStatus"), Art.STATUS_CANCELED
        ));
        if (criteria.query() != null) {
            predicates.add(builder.like(
                    builder.lower(art.get("name")),
                    contains(criteria.query())
            ));
        }
        if (criteria.artist() != null) {
            predicates.add(builder.like(
                    builder.lower(artist.get("artistName")),
                    contains(criteria.artist())
            ));
        }
        if (criteria.format() != null) {
            predicates.add(builder.equal(art.get("format"), criteria.format()));
        }
        if (criteria.category() != null) {
            predicates.add(builder.equal(art.get("category"), criteria.category()));
        }
        if (criteria.status() != null) {
            predicates.add(statusPredicate(builder, art, criteria.status(), now));
        }
        return predicates;
    }

    private Predicate statusPredicate(
            CriteriaBuilder builder,
            Root<Art> art,
            ArtSearchStatus status,
            LocalDateTime now) {
        Expression<Integer> artStatus = art.get("artStatus");
        Expression<LocalDateTime> bidStart = art.get("bidStartTime");
        Expression<LocalDateTime> closing = art.get("closingTime");
        return switch (status) {
            case UPCOMING -> builder.and(
                    builder.equal(artStatus, Art.STATUS_ACTIVE),
                    builder.greaterThan(bidStart, now),
                    builder.greaterThan(closing, now)
            );
            case ONGOING -> builder.and(
                    builder.equal(artStatus, Art.STATUS_ACTIVE),
                    builder.lessThanOrEqualTo(bidStart, now),
                    builder.greaterThan(closing, now)
            );
            case ENDED -> builder.or(
                    artStatus.in(Art.STATUS_UNSOLD, Art.STATUS_SOLD),
                    builder.and(
                            builder.equal(artStatus, Art.STATUS_ACTIVE),
                            builder.lessThanOrEqualTo(closing, now)
                    )
            );
        };
    }

    private List<Order> orders(
            CriteriaBuilder builder,
            Root<Art> art,
            ArtSearchSort sort,
            LocalDateTime now) {
        ArtSearchSort selected = sort == null ? ArtSearchSort.ENDING_SOON : sort;
        List<Order> orders = new ArrayList<>();
        switch (selected) {
            case ENDING_SOON -> {
                Expression<Integer> ended = builder.<Integer>selectCase()
                        .when(builder.or(
                                art.get("artStatus").in(
                                        Art.STATUS_UNSOLD, Art.STATUS_SOLD
                                ),
                                builder.lessThanOrEqualTo(
                                        art.get("closingTime"), now
                                )
                        ), 1)
                        .otherwise(0);
                orders.add(builder.asc(ended));
                orders.add(builder.asc(art.get("closingTime")));
            }
            case NEWEST -> orders.add(builder.desc(art.get("createdAt")));
            case PRICE_ASC -> orders.add(builder.asc(art.get("currentPrice")));
            case PRICE_DESC -> orders.add(builder.desc(art.get("currentPrice")));
        }
        orders.add(builder.desc(art.get("artId")));
        return orders;
    }

    private String contains(String value) {
        return "%" + value.toLowerCase() + "%";
    }
}

package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.dto.LikeItemDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Likes;
import com.dailyatelier.dailyatelier.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LikesRepository extends JpaRepository<Likes, Long> {
    boolean existsByUserAndArt(User user, Art art);

    Optional<Likes> findByUserAndArt(User user, Art art);

    void deleteByUserAndArt(User user, Art art);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Likes l set l.art = null where l.art = :art")
    int detachArt(@Param("art") Art art);

    @Query(
            value = """
                    select new com.dailyatelier.dailyatelier.dto.LikeItemDto(
                        l.likesId,
                        a.artId,
                        a.name,
                        a.imgPath,
                        ar.artistName,
                        a.currentPrice,
                        a.closingTime,
                        a.artStatus,
                        case when a is null then true else false end,
                        case when a is null then '없어진 작품입니다.' else null end
                    )
                    from Likes l
                    left join l.art a
                    left join a.artist ar
                    where l.user.userId = :userId
                    order by l.likesId desc
                    """,
            countQuery = """
                    select count(l)
                    from Likes l
                    where l.user.userId = :userId
                    """
    )
    Page<LikeItemDto> findLikeItemsByUserId(@Param("userId") String userId, Pageable pageable);
}

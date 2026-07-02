package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.dto.LikeItemDto;
import com.dailyatelier.dailyatelier.entity.Art;
import com.dailyatelier.dailyatelier.entity.Likes;
import com.dailyatelier.dailyatelier.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LikesRepository extends JpaRepository<Likes, Long> {
    boolean existsByUserAndArt(User user, Art art);

    Optional<Likes> findByUserAndArt(User user, Art art);

    void deleteByUserAndArt(User user, Art art);

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
                        a.artStatus
                    )
                    from Likes l
                    join l.art a
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

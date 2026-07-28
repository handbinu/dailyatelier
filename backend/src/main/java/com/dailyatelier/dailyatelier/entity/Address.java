package com.dailyatelier.dailyatelier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "address")
@Getter @Setter
@NoArgsConstructor
public class Address {

    @Id
    @Column(name = "user_id", length = 45)
    private String userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "zip_code", length = 5)
    private String zipCode;

    @Column(name = "user_address1", length = 100)
    private String userAddress1;

    @Column(name = "user_address2", length = 100)
    private String userAddress2;
}

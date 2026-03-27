import { useNavigate } from 'react-router-dom'
import styles from './RegisterSelect.module.css'
import userIcon from '../../assets/user.png';
import artistIcon from '../../assets/artist.png';

function RegisterSelect() {
    const navigate = useNavigate()

    return(
        <div className={styles.wrap}>
            <h1 className={styles.title}>회원가입</h1>
            <hr className={styles.hr}/>
            <p className={styles.sub}>
                일반 회원으로 가입하여도 추후에 작가 회원으로 변경할 수 있습니다.
            </p>

            <div className={styles.cardWrap}>
                <div className={styles.card} onClick={() => navigate('/register/user')}>
                    <img src={userIcon} alt="일반회원"/>
                    <button className={styles.cardBtn}>일반 회원</button>
                </div>

                <div className={styles.card} onClick={() => navigate('/register/artist')}>
                    <img src={artistIcon} alt="작가회원"/>
                    <button className={styles.cardBtn}>작가 회원</button>
                </div>

            </div>
        </div> 
    )
}
 
export default RegisterSelect
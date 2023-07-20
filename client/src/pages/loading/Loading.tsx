import { useEffect, useContext } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useLocalStorage, useReadLocalStorage } from 'usehooks-ts';
import LoadingComponent from '../../components/loading/LoadingComponent';
import { ContextDispatch, MemberIdDispatchContext } from '../../store/Context';

export function Component() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [, setAccessToken] = useLocalStorage<string | null>('accessToken', '');
  const [, setRefreshToken] = useLocalStorage<string | null>(
    'refreshToken',
    '',
  );
  const firstVisited = useReadLocalStorage('firstVisited');

  // local에 accessToken이 있는지 확인하기
  const dispatch = useContext(MemberIdDispatchContext);

  useEffect(() => {
    if (searchParams.size !== 0) {
      setAccessToken(searchParams.get('accessToken'));
      setRefreshToken(searchParams.get('refreshToken'));
      const memberId = searchParams.get('memberId');

      (dispatch as ContextDispatch)({
        memberId: memberId as string,
        type: 'NOT_TOKEN',
      });

      const present = searchParams.get('present');
      if (present === 'true') setTimeout(() => navigate('/home'), 1000);
      else {
        const incodeName: string | null = searchParams.get('name');
        const name = incodeName ? decodeURIComponent(incodeName) : '';
        const email = searchParams.get('email');
        navigate('/info', {
          state: {
            name,
            email,
          },
        });
      }

      if (!firstVisited) {
        localStorage.setItem('firstVisited', 'true');
      }
    }
  }, [
    setAccessToken,
    setRefreshToken,
    searchParams,
    navigate,
    dispatch,
    firstVisited,
  ]);

  return <LoadingComponent />;
}

Component.displayName = 'Loading';

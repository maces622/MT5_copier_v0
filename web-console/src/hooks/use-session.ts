import { useQuery } from '@tanstack/react-query'
import { getCurrentUser } from '../lib/api'
import { isUnauthorized } from '../lib/http'

export function useCurrentUser() {
  return useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getCurrentUser,
    retry: (failureCount, error) => !isUnauthorized(error) && failureCount < 1,
  })
}

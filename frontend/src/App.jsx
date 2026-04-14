import { useState } from 'react'
import MainView from './components/MainView'
import LoadingOverlay from './components/LoadingOverlay'
import { signInWithPopup } from 'firebase/auth'
import { auth, googleProvider } from './firebase'

export default function App() {
  const [loadingText, setLoadingText] = useState('')
  const [loading, setLoading] = useState(false)

  const handleGoogle = async () => {
    try {
      setLoadingText('Opening Google sign-in...')
      setLoading(true)

      const result = await signInWithPopup(auth, googleProvider)
      const user = result.user

      // Firebase popup succeeded — redirect to local server callback to persist the session.
      if (user) {
        window.location.href = '/callback'
        return
      }

      setLoading(false)
    } catch (err) {
      setLoadingText('Google sign-in failed. Try again.')
      setTimeout(() => setLoading(false), 1200)
    }
  }

  const handleGitHub = () => {
    setLoadingText('Redirecting to GitHub...')
    setLoading(true)
    setTimeout(() => {
      window.location.href = '/auth/github'
    }, 400)
  }

  const handleGuest = () => {
    setLoadingText('Setting up guest session...')
    setLoading(true)
    setTimeout(() => {
      window.location.href = '/callback'
    }, 400)
  }

  return (
    <>
      <LoadingOverlay text={loadingText} visible={loading} />

      <div className="page-wrapper">
        <MainView
          onGoogle={handleGoogle}
          onGitHub={handleGitHub}
          onGuest={handleGuest}
        />
      </div>

      <div className="footer">VaultFS &middot; Secure File System Simulator</div>
    </>
  )
}

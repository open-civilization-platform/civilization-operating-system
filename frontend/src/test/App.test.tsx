import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { MockedProvider } from '@apollo/client/testing'
import App from '../App'

function renderApp() {
  return render(
    <MockedProvider mocks={[]} addTypename={false}>
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    </MockedProvider>
  )
}

describe('App responsive navigation', () => {
  it('renders a mobile menu toggle button', () => {
    renderApp()
    expect(screen.getByLabelText('Open navigation menu')).toBeInTheDocument()
  })

  it('keeps the sidebar off-screen until the hamburger is opened', () => {
    renderApp()
    const nav = screen.getByRole('navigation')
    expect(nav.className).toContain('-translate-x-full')
  })

  it('slides the sidebar into view when the hamburger is tapped', () => {
    renderApp()
    fireEvent.click(screen.getByLabelText('Open navigation menu'))
    const nav = screen.getByRole('navigation')
    expect(nav.className).toContain('translate-x-0')
    expect(nav.className).not.toContain('-translate-x-full')
  })

  it('shows a backdrop while the mobile menu is open, and closes on click', () => {
    renderApp()
    fireEvent.click(screen.getByLabelText('Open navigation menu'))
    const closeButton = screen.getByLabelText('Close navigation menu')
    expect(closeButton).toBeInTheDocument()

    fireEvent.click(closeButton)
    const nav = screen.getByRole('navigation')
    expect(nav.className).toContain('-translate-x-full')
  })

  it('closes the mobile menu when a nav link is clicked', async () => {
    renderApp()
    fireEvent.click(screen.getByLabelText('Open navigation menu'))
    fireEvent.click(screen.getByText('Trade'))
    // The Trade page is lazy-loaded, so the route (and the close-on-navigate
    // effect) only commits once the chunk has finished resolving.
    await screen.findByText('Trade Agreements')
    await waitFor(() => {
      const nav = screen.getByRole('navigation')
      expect(nav.className).toContain('-translate-x-full')
    })
  })

  it('renders all primary nav items with touch-friendly (44px) targets', () => {
    renderApp()
    const dashboardLink = screen.getByText('Dashboard').closest('a')
    expect(dashboardLink).toHaveStyle({ minHeight: '44px' })
  })
})

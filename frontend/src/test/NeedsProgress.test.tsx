import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import NeedsProgress from '../components/NeedsProgress'

describe('NeedsProgress', () => {
  it('renders title and default needs bars', () => {
    render(<NeedsProgress />)
    expect(screen.getByText('Population Needs')).toBeInDocument()
    expect(screen.getByText('Housing')).toBeInDocument()
    expect(screen.getByText('Food')).toBeInDocument()
    expect(screen.getByText('Water')).toBeInDocument()
    expect(screen.getByText('Energy')).toBeInDocument()
  })

  it('renders custom percentage indicators correctly', () => {
    const customNeeds = { housing: 95, food: 45, water: 70, energy: 30 }
    render(<NeedsProgress needs={customNeeds} title="Custom Needs" />)
    expect(screen.getByText('Custom Needs')).toBeInDocument()
    expect(screen.getByText('95%')).toBeInDocument()
    expect(screen.getByText('45%')).toBeInDocument()
    expect(screen.getByText('70%')).toBeInDocument()
    expect(screen.getByText('30%')).toBeInDocument()
  })
})

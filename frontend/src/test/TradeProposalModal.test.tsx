import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import TradeProposalModal from '../components/TradeProposalModal'

describe('TradeProposalModal', () => {
  it('does not render when isOpen is false', () => {
    render(<TradeProposalModal isOpen={false} onClose={() => {}} />)
    expect(screen.queryByText('Propose Trade Agreement')).not.toBeInDocument()
  })

  it('renders modal elements when isOpen is true', () => {
    render(<TradeProposalModal isOpen={true} onClose={() => {}} />)
    expect(screen.getByText('Propose Trade Agreement')).toBeInDocument()
    expect(screen.getByLabelText('Target Civilization')).toBeInDocument()
    expect(screen.getByLabelText('Resource Type')).toBeInDocument()
    expect(screen.getByLabelText('Quantity / Volume')).toBeInDocument()
    expect(screen.getByRole('button', { name: /Submit Proposal/i })).toBeInDocument()
  })

  it('calls onClose when Cancel button is clicked', () => {
    const handleClose = vi.fn()
    render(<TradeProposalModal isOpen={true} onClose={handleClose} />)
    const cancelBtn = screen.getByRole('button', { name: 'Cancel' })
    fireEvent.click(cancelBtn)
    expect(handleClose).toHaveBeenCalledOnce()
  })
})

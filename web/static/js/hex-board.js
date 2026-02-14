/**
 * HexBoard - Canvas-based hex grid renderer
 */
class HexBoard {
    constructor(canvasId, size = 11) {
        this.canvas = document.getElementById(canvasId);
        this.ctx = this.canvas.getContext('2d');
        this.size = size;
        this.boardState = Array(size).fill(null).map(() => Array(size).fill(null));
        
        // Hex geometry
        this.hexRadius = 25;
        this.hexHeight = this.hexRadius * 2;
        this.hexWidth = Math.sqrt(3) * this.hexRadius;
        this.vertDist = this.hexHeight * 0.75;
        this.horizDist = this.hexWidth;
        
        // Colors
        this.colors = {
            RED: '#e74c3c',
            BLUE: '#3498db',
            EMPTY: '#ecf0f1',
            HOVER: '#bdc3c7',
            BORDER: '#34495e',
            RED_EDGE: '#c0392b',
            BLUE_EDGE: '#2980b9'
        };
        
        // Interaction
        this.hoveredCell = null;
        this.onClick = null;
        
        this.setupCanvas();
        this.setupEventListeners();
        this.draw();
    }
    
    setupCanvas() {
        // Calculate total canvas size needed
        const totalWidth = this.horizDist * (this.size + 1) + this.hexRadius * 2;
        const totalHeight = this.vertDist * (this.size + 1) + this.hexRadius * 2;
        
        this.canvas.width = totalWidth;
        this.canvas.height = totalHeight;
        
        // Offset for centering
        this.offsetX = this.hexRadius * 1.5;
        this.offsetY = this.hexRadius * 1.5;
    }
    
    setupEventListeners() {
        this.canvas.addEventListener('mousemove', (e) => this.handleMouseMove(e));
        this.canvas.addEventListener('click', (e) => this.handleClick(e));
        this.canvas.addEventListener('mouseleave', () => {
            this.hoveredCell = null;
            this.draw();
        });
    }
    
    /**
     * Convert canvas coordinates to hex cell
     */
    pixelToHex(x, y) {
        x -= this.offsetX;
        y -= this.offsetY;
        
        // Check each cell
        for (let row = 0; row < this.size; row++) {
            for (let col = 0; col < this.size; col++) {
                const hexCenter = this.getHexCenter(row, col);
                const dist = Math.sqrt(
                    Math.pow(x - hexCenter.x, 2) + 
                    Math.pow(y - hexCenter.y, 2)
                );
                
                if (dist < this.hexRadius) {
                    return { row, col };
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get center coordinates of a hex cell
     */
    getHexCenter(row, col) {
        const x = col * this.horizDist + row * (this.horizDist / 2);
        const y = row * this.vertDist;
        return { x, y };
    }
    
    /**
     * Draw a hexagon at given center
     */
    drawHexagon(x, y, color, borderColor = null) {
        this.ctx.beginPath();
        
        for (let i = 0; i < 6; i++) {
            const angle = Math.PI / 3 * i - Math.PI / 6;
            const hx = x + this.hexRadius * Math.cos(angle);
            const hy = y + this.hexRadius * Math.sin(angle);
            
            if (i === 0) {
                this.ctx.moveTo(hx, hy);
            } else {
                this.ctx.lineTo(hx, hy);
            }
        }
        
        this.ctx.closePath();
        
        // Fill
        this.ctx.fillStyle = color;
        this.ctx.fill();
        
        // Border
        this.ctx.strokeStyle = borderColor || this.colors.BORDER;
        this.ctx.lineWidth = 2;
        this.ctx.stroke();
    }
    
    /**
     * Draw coordinate labels
     */
    drawLabels() {
        this.ctx.fillStyle = '#2c3e50';
        this.ctx.font = '12px Arial';
        this.ctx.textAlign = 'center';
        this.ctx.textBaseline = 'middle';
        
        // Draw row/col numbers
        for (let i = 0; i < this.size; i++) {
            const topCell = this.getHexCenter(0, i);
            const leftCell = this.getHexCenter(i, 0);
            
            // Column labels (top)
            this.ctx.fillText(
                i.toString(),
                this.offsetX + topCell.x,
                this.offsetY - this.hexRadius - 15
            );
            
            // Row labels (left)
            this.ctx.fillText(
                i.toString(),
                this.offsetX - this.hexRadius - 15,
                this.offsetY + leftCell.y
            );
        }
    }
    
    /**
     * Draw edge indicators for RED (top-bottom) and BLUE (left-right)
     */
    drawEdges() {
        const ctx = this.ctx;
        
        // RED edges (top and bottom)
        ctx.strokeStyle = this.colors.RED_EDGE;
        ctx.lineWidth = 6;
        ctx.lineCap = 'round';
        
        // Top edge
        ctx.beginPath();
        const topLeft = this.getHexCenter(0, 0);
        const topRight = this.getHexCenter(0, this.size - 1);
        ctx.moveTo(this.offsetX + topLeft.x - this.hexRadius, this.offsetY + topLeft.y);
        ctx.lineTo(this.offsetX + topRight.x + this.hexRadius, this.offsetY + topRight.y);
        ctx.stroke();
        
        // Bottom edge
        ctx.beginPath();
        const bottomLeft = this.getHexCenter(this.size - 1, 0);
        const bottomRight = this.getHexCenter(this.size - 1, this.size - 1);
        ctx.moveTo(this.offsetX + bottomLeft.x - this.hexRadius, this.offsetY + bottomLeft.y);
        ctx.lineTo(this.offsetX + bottomRight.x + this.hexRadius, this.offsetY + bottomRight.y);
        ctx.stroke();
        
        // BLUE edges (left and right)
        ctx.strokeStyle = this.colors.BLUE_EDGE;
        
        // Left edge
        ctx.beginPath();
        const leftTop = this.getHexCenter(0, 0);
        const leftBottom = this.getHexCenter(this.size - 1, 0);
        ctx.moveTo(this.offsetX + leftTop.x, this.offsetY + leftTop.y - this.hexRadius);
        ctx.lineTo(this.offsetX + leftBottom.x, this.offsetY + leftBottom.y + this.hexRadius);
        ctx.stroke();
        
        // Right edge
        ctx.beginPath();
        const rightTop = this.getHexCenter(0, this.size - 1);
        const rightBottom = this.getHexCenter(this.size - 1, this.size - 1);
        ctx.moveTo(this.offsetX + rightTop.x, this.offsetY + rightTop.y - this.hexRadius);
        ctx.lineTo(this.offsetX + rightBottom.x, this.offsetY + rightBottom.y + this.hexRadius);
        ctx.stroke();
    }
    
    /**
     * Main draw function
     */
    draw() {
        // Clear canvas
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        
        // Draw edge indicators first
        this.drawEdges();
        
        // Draw all hexagons
        for (let row = 0; row < this.size; row++) {
            for (let col = 0; col < this.size; col++) {
                const center = this.getHexCenter(row, col);
                const x = this.offsetX + center.x;
                const y = this.offsetY + center.y;
                
                let color = this.colors.EMPTY;
                
                // Check cell state
                if (this.boardState[row][col] === 'RED') {
                    color = this.colors.RED;
                } else if (this.boardState[row][col] === 'BLUE') {
                    color = this.colors.BLUE;
                } else if (this.hoveredCell && 
                           this.hoveredCell.row === row && 
                           this.hoveredCell.col === col) {
                    color = this.colors.HOVER;
                }
                
                this.drawHexagon(x, y, color);
            }
        }
        
        // Draw labels
        this.drawLabels();
    }
    
    /**
     * Handle mouse movement for hover effect
     */
    handleMouseMove(e) {
        const rect = this.canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        
        const cell = this.pixelToHex(x, y);
        
        if (cell && this.boardState[cell.row][cell.col] === null) {
            this.hoveredCell = cell;
        } else {
            this.hoveredCell = null;
        }
        
        this.draw();
    }
    
    /**
     * Handle click events
     */
    handleClick(e) {
        const rect = this.canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        
        const cell = this.pixelToHex(x, y);
        
        if (cell && this.boardState[cell.row][cell.col] === null) {
            if (this.onClick) {
                this.onClick(cell.row, cell.col);
            }
        }
    }
    
    /**
     * Set a cell color
     */
    setCell(row, col, color) {
        if (row >= 0 && row < this.size && col >= 0 && col < this.size) {
            this.boardState[row][col] = color;
            this.draw();
        }
    }
    
    /**
     * Update entire board state
     */
    setBoardState(state) {
        this.boardState = state;
        this.draw();
    }
    
    /**
     * Reset board
     */
    reset(newSize = null) {
        if (newSize && newSize !== this.size) {
            this.size = newSize;
            this.setupCanvas();
        }
        
        this.boardState = Array(this.size).fill(null).map(() => Array(this.size).fill(null));
        this.hoveredCell = null;
        this.draw();
    }
    
    /**
     * Set click handler
     */
    setClickHandler(callback) {
        this.onClick = callback;
    }
    
    /**
     * Enable/disable interaction
     */
    setInteractive(enabled) {
        this.canvas.style.pointerEvents = enabled ? 'auto' : 'none';
    }
}
